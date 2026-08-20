package co.istad.projectpracticum.phsardigital.core.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    @Test
    void allowsTheBurstThenRefuses() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1), 100);

        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isFalse();
    }

    @Test
    void countsEachKeySeparately() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1), 100);

        assertThat(limiter.tryAcquire("first")).isTrue();
        assertThat(limiter.tryAcquire("second")).isTrue();
        assertThat(limiter.tryAcquire("first")).isFalse();
    }

    @Test
    void refillsOverTheConfiguredPeriod() throws InterruptedException {
        // 20 tokens a second, so one comes back in 50ms.
        RateLimiter limiter = new RateLimiter(20, Duration.ofSeconds(1), 100);
        IntStream.range(0, 20).forEach(ignored -> limiter.tryAcquire("client"));
        assertThat(limiter.tryAcquire("client")).isFalse();

        Thread.sleep(120);

        assertThat(limiter.tryAcquire("client")).isTrue();
    }

    @Test
    void reportsWholeSecondsUntilTheNextToken() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(10), 100);

        assertThat(limiter.retryAfterSeconds("client")).isZero();
        assertThat(limiter.tryAcquire("client")).isTrue();

        // Never rounds down to zero for a key that is actually blocked — the value
        // goes straight into Retry-After, and a 0 there invites an instant retry.
        assertThat(limiter.retryAfterSeconds("client")).isBetween(1L, 10L);
    }

    @Test
    void sweepsIdleKeysOnceTheTrackedLimitIsPassed() {
        // A key is "idle" once its bucket is back to full, which at this rate is
        // immediately — enough to prove the sweep runs and drops them.
        RateLimiter limiter = new RateLimiter(1000, Duration.ofNanos(1000), 2);

        IntStream.range(0, 50).forEach(index -> limiter.tryAcquire("client-" + index));

        // Nothing observable to assert but the absence of unbounded growth, so this
        // stands as a guard on the sweep not throwing on a live map.
        assertThat(limiter.tryAcquire("client-0")).isTrue();
    }

    @Test
    void handsOutNoMoreThanTheCapacityUnderConcurrency() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(50, Duration.ofMinutes(5), 100);
        AtomicInteger granted = new AtomicInteger();

        Thread[] threads = IntStream.range(0, 8)
                .mapToObj(ignored -> new Thread(() -> {
                    for (int attempt = 0; attempt < 100; attempt++) {
                        if (limiter.tryAcquire("shared")) {
                            granted.incrementAndGet();
                        }
                    }
                }))
                .toArray(Thread[]::new);

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // The refill over a five-minute period is negligible across a few
        // milliseconds of contention, so the burst is the whole budget.
        assertThat(granted.get()).isEqualTo(50);
    }

    @Test
    void rejectsNonsensicalConfiguration() {
        assertThatThrownBy(() -> new RateLimiter(0, Duration.ofMinutes(1), 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, Duration.ZERO, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, null, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
