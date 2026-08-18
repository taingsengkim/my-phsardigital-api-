package co.istad.projectpracticum.phsardigital.features.seller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the coordinates out of a Google Maps link, so a shop can be pinned by pasting
 * the link the Maps app hands them rather than by knowing its latitude.
 */
@Component
@Slf4j
public class GoogleMapsLinkParser {

    /**
     * The pin on a place page, carried in the {@code data=} blob. Tried first because
     * it is the place itself, whereas {@link #VIEWPORT} is only where the camera
     * happened to be — on a place page the two differ, sometimes by a street.
     */
    private static final Pattern PIN =
            Pattern.compile("!3d(-?\\d{1,3}(?:\\.\\d+)?)!4d(-?\\d{1,3}(?:\\.\\d+)?)");

    /** Coordinates passed as a parameter, as the sharing and directions URLs do. */
    private static final Pattern QUERY = Pattern.compile(
            "[?&](?:q|query|destination|ll|center|sll|daddr)="
                    + "(-?\\d{1,3}(?:\\.\\d+)?)(?:,|%2C)(-?\\d{1,3}(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    /** The {@code @lat,lng,zoom} viewport in a browser URL. */
    private static final Pattern VIEWPORT =
            Pattern.compile("@(-?\\d{1,3}(?:\\.\\d+)?),(-?\\d{1,3}(?:\\.\\d+)?)");

    /**
     * Hosts whose links are a redirect to the real one. This is what the mobile app's
     * Share button produces, so without expanding these the feature would miss the way
     * most sellers will actually use it.
     */
    private static final Set<String> SHORT_LINK_HOSTS = Set.of("maps.app.goo.gl", "goo.gl");

    /**
     * Hosts a redirect is allowed to lead to. The URL comes from a user, so following
     * it anywhere would make this endpoint a request forwarder pointed at our own
     * network; the allowlist is what keeps it aimed at Google.
     */
    private static final Set<String> GOOGLE_HOSTS = Set.of(
            "google.com", "www.google.com", "maps.google.com", "maps.app.goo.gl", "goo.gl");

    private static final int MAX_REDIRECTS = 3;

    @Value("${app.maps.resolve-short-links:true}")
    private boolean resolveShortLinks;

    @Value("${app.maps.short-link-timeout:3s}")
    private Duration shortLinkTimeout;

    /**
     * @param url a Google Maps link in any of the forms the site and app produce
     * @return the coordinates it points at, or empty when the link carries none — a
     *         search for a business by name, for instance, has no position in it
     */
    public Optional<Coordinates> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        String resolved = isShortLink(url) ? expand(url).orElse(url) : url;

        return firstMatch(PIN, resolved)
                .or(() -> firstMatch(QUERY, resolved))
                .or(() -> firstMatch(VIEWPORT, resolved));
    }

    private Optional<Coordinates> firstMatch(Pattern pattern, String url) {
        Matcher matcher = pattern.matcher(url);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            BigDecimal latitude = new BigDecimal(matcher.group(1));
            BigDecimal longitude = new BigDecimal(matcher.group(2));
            return inRange(latitude, longitude)
                    ? Optional.of(new Coordinates(latitude, longitude))
                    : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /**
     * Guards against matching something shaped like coordinates but not being any —
     * a zoom level or an id can sit in the same position in these URLs.
     */
    private boolean inRange(BigDecimal latitude, BigDecimal longitude) {
        return latitude.abs().compareTo(BigDecimal.valueOf(90)) <= 0
                && longitude.abs().compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private boolean isShortLink(String url) {
        return hostOf(url).map(SHORT_LINK_HOSTS::contains).orElse(false);
    }

    /**
     * Follows the redirect chain by hand rather than letting the client do it, so each
     * hop can be checked against {@link #GOOGLE_HOSTS} before it is requested. The body
     * is discarded — only the {@code Location} header matters.
     */
    private Optional<String> expand(String shortUrl) {
        if (!resolveShortLinks) {
            return Optional.empty();
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(shortLinkTimeout)
                .build();

        String current = shortUrl;
        try {
            for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
                if (!hostOf(current).map(GOOGLE_HOSTS::contains).orElse(false)) {
                    return Optional.empty();
                }

                HttpResponse<Void> response = client.send(
                        HttpRequest.newBuilder(URI.create(current))
                                .timeout(shortLinkTimeout)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.discarding());

                Optional<String> location = response.headers().firstValue("location");
                if (location.isEmpty()) {
                    // Not a redirect: this is as far as the chain goes.
                    return Optional.of(current);
                }
                current = location.get();
            }
            return Optional.of(current);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception unreachable) {
            // A shop should not be unable to save its profile because Google is slow.
            log.warn("Could not expand Maps short link {}: {}", shortUrl, unreachable.toString());
            return Optional.empty();
        }
    }

    private Optional<String> hostOf(String url) {
        try {
            return Optional.ofNullable(URI.create(url).getHost());
        } catch (IllegalArgumentException notAUrl) {
            return Optional.empty();
        }
    }

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }
}
