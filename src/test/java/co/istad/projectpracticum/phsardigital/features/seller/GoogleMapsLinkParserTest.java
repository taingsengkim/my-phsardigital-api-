package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.GoogleMapsLinkParser.Coordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the link shapes Google Maps actually hands a user. Short links are not
 * exercised here — expanding one is a network call, and the parsing this tests happens
 * after that hop, on whatever URL it lands at.
 */
class GoogleMapsLinkParserTest {

    private final GoogleMapsLinkParser parser = new GoogleMapsLinkParser();

    @Test
    @DisplayName("reads the pin off a place page")
    void readsPlacePin() {
        Optional<Coordinates> result = parser.parse(
                "https://www.google.com/maps/place/Central+Market/@11.5691,104.9212,17z"
                        + "/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d11.5695!4d104.9215");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualByComparingTo(new BigDecimal("11.5695"));
        assertThat(result.get().longitude()).isEqualByComparingTo(new BigDecimal("104.9215"));
    }

    @Test
    @DisplayName("prefers the pin over the viewport when a page carries both")
    void prefersPinOverViewport() {
        // The @ coordinates are where the camera sat; !3d/!4d is the place itself.
        Optional<Coordinates> result = parser.parse(
                "https://www.google.com/maps/place/Shop/@11.0000,104.0000,17z/data=!3d11.5695!4d104.9215");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualByComparingTo(new BigDecimal("11.5695"));
    }

    @Test
    @DisplayName("falls back to the viewport when there is no pin")
    void readsViewport() {
        Optional<Coordinates> result = parser.parse("https://www.google.com/maps/@11.5564,104.9282,15z");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualByComparingTo(new BigDecimal("11.5564"));
        assertThat(result.get().longitude()).isEqualByComparingTo(new BigDecimal("104.9282"));
    }

    @ParameterizedTest
    @DisplayName("reads coordinates passed as a query parameter")
    @ValueSource(strings = {
            "https://www.google.com/maps?q=11.5564,104.9282",
            "https://maps.google.com/?q=11.5564,104.9282",
            "https://www.google.com/maps/search/?api=1&query=11.5564,104.9282",
            "https://www.google.com/maps/dir/?api=1&destination=11.5564,104.9282",
            "https://www.google.com/maps/search/?api=1&query=11.5564%2C104.9282"
    })
    void readsQueryParameter(String url) {
        Optional<Coordinates> result = parser.parse(url);

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualByComparingTo(new BigDecimal("11.5564"));
        assertThat(result.get().longitude()).isEqualByComparingTo(new BigDecimal("104.9282"));
    }

    @Test
    @DisplayName("handles the southern and western hemispheres")
    void readsNegativeCoordinates() {
        Optional<Coordinates> result = parser.parse("https://www.google.com/maps/@-33.8688,-151.2093,15z");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualByComparingTo(new BigDecimal("-33.8688"));
        assertThat(result.get().longitude()).isEqualByComparingTo(new BigDecimal("-151.2093"));
    }

    @Test
    @DisplayName("rejects numbers that cannot be coordinates")
    void rejectsOutOfRange() {
        assertThat(parser.parse("https://www.google.com/maps/@999.1234,104.9282,15z")).isEmpty();
    }

    @Test
    @DisplayName("returns empty for a search that names a place instead of pointing at one")
    void emptyForNameOnlySearch() {
        assertThat(parser.parse("https://www.google.com/maps/search/coffee+shops+phnom+penh")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not a url at all", "https://example.com/somewhere"})
    @DisplayName("returns empty rather than throwing on anything unusable")
    void emptyForUnusableInput(String url) {
        assertThat(parser.parse(url)).isEmpty();
    }
}
