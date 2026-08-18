package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.GoogleMapsLinkParser.Coordinates;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Decides where a shop sits, from the two ways a seller can say so: dropping a pin on
 * a map, which arrives as coordinates, or pasting the link Google Maps gave them.
 *
 * <p>One class rather than the rule written out at each call site, because applying to
 * become a seller and later editing the shop both set a location and must agree about
 * what a half-filled request means.
 */
@Component
@RequiredArgsConstructor
public class ShopLocationResolver {

    private final GoogleMapsLinkParser linkParser;

    /**
     * @return the coordinates to store, or empty when the request said nothing about
     *         location — which on a PATCH means leave whatever is already there
     * @throws ResponseStatusException 400 when only one half of a coordinate pair was
     *         sent, or when a link was sent that carries no position
     */
    public Optional<Coordinates> resolve(BigDecimal latitude, BigDecimal longitude, String googleMapUrl) {
        boolean hasLatitude = latitude != null;
        boolean hasLongitude = longitude != null;

        if (hasLatitude != hasLongitude) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Latitude and longitude must be sent together.");
        }

        // An explicit pin wins over the link: the seller moved the marker deliberately,
        // and a link pasted alongside it is likelier to be stale than to be a correction.
        if (hasLatitude) {
            return Optional.of(new Coordinates(latitude, longitude));
        }

        if (googleMapUrl == null || googleMapUrl.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(linkParser.parse(googleMapUrl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That Google Maps link does not contain a location. Open the place in "
                                + "Google Maps and use Share, or drop a pin and send latitude and "
                                + "longitude instead.")));
    }
}
