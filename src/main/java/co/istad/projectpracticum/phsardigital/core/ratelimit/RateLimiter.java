package co.istad.projectpracticum.phsardigital.core.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket per key, held in memory.
 *
 * <p>Each key starts with {@code capacity} tokens and earns them back steadily, a
 * full bucket's worth every {@code period}. That shape is deliberate: a burst of
 * legitimate activity — someone fat-fingering a registration form three times in a
 * row — passes untouched, while a caller hammering the endpoint settles down to the
 * long-run rate instead of being let through in bursts at the top of every window.
 *
 * <p>Deliberately not backed by Redis. This counts per process, so a deployment
 * behind two instances tolerates roughly twice the configured rate — which is fine
 * for what this defends against (one host sending thousands of registrations or
 * password-reset mails) and not fine for anything that needs an exact quota. Move it
 * to a shared store before using it for billing or per-tenant limits.
 */
public class RateLimiter {

    private final int capacity;
    private final double tokensPerNano;
    private final int maxTrackedKeys;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * @param capacity       how many requests a single key may make back to back
     * @param period         how long a fully drained bucket takes to refill
     * @param maxTrackedKeys the point at which idle keys are swept, bounding how much
     *                       memory an attacker can occupy by rotating source addresses
     */
    public RateLimiter(int capacity, Duration period, int maxTrackedKeys) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Rate limit capacity must be at least 1");
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("Rate limit period must be positive");
        }
        this.capacity = capacity;
        this.tokensPerNano = (double) capacity / period.toNanos();
        this.maxTrackedKeys = Math.max(maxTrackedKeys, 1);
    }

    /**
     * Takes one token for the key.
     *
     * @return true when the request is within the limit, false when it is not
     */
    public boolean tryAcquire(String key) {
        if (buckets.size() > maxTrackedKeys) {
            evictIdleBuckets();
        }
        return buckets.computeIfAbsent(key, ignored -> new Bucket()).tryConsume();
    }

    /**
     * @return whole seconds until the key can spend a token again, or 0 when it can
     * already. Never rounds down to 0 for a key that is actually blocked, so it is
     * safe to put straight into a {@code Retry-After} header.
     */
    public long retryAfterSeconds(String key) {
        Bucket bucket = buckets.get(key);
        return bucket == null ? 0 : bucket.retryAfterSeconds();
    }

    /**
     * Drops every key that is back to full, which is the same thing as "has not been
     * seen for a whole period". A bucket another thread is holding may be dropped
     * mid-use; the worst outcome is that one consumption is forgotten, and only ever
     * for a key that was not near its limit anyway.
     */
    private void evictIdleBuckets() {
        buckets.values().removeIf(Bucket::isFull);
    }

    private final class Bucket {

        private double tokens = capacity;
        private long lastRefillNanos = System.nanoTime();

        synchronized boolean tryConsume() {
            refill();
            if (tokens < 1.0d) {
                return false;
            }
            tokens -= 1.0d;
            return true;
        }

        synchronized boolean isFull() {
            refill();
            return tokens >= capacity;
        }

        synchronized long retryAfterSeconds() {
            refill();
            if (tokens >= 1.0d) {
                return 0;
            }
            double nanosToWait = (1.0d - tokens) / tokensPerNano;
            return (long) Math.ceil(nanosToWait / 1_000_000_000d);
        }

        /**
         * {@link System#nanoTime()} rather than the wall clock, so an NTP correction
         * cannot hand out a windfall of tokens or freeze a key out for hours.
         */
        private void refill() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * tokensPerNano);
            lastRefillNanos = now;
        }
    }
}
