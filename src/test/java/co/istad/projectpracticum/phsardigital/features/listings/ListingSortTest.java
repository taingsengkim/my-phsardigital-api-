package co.istad.projectpracticum.phsardigital.features.listings;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a client may sort the catalogue by, and what attribute each name resolves to —
 * a name that no longer matches a field on {@link Listing} fails inside the repository
 * rather than here.
 */
class ListingSortTest {

    private static String propertyOf(Sort sort) {
        return sort.stream().findFirst().orElseThrow().getProperty();
    }

    private static Sort.Direction directionOf(Sort sort) {
        return sort.stream().findFirst().orElseThrow().getDirection();
    }

    @Test
    void sortsByNewestWhenNothingIsAsked() {
        assertThat(ListingSort.parse(null)).isEqualTo(Sort.by(Sort.Direction.DESC, "lastModifiedAt"));
        assertThat(ListingSort.parse("  ")).isEqualTo(Sort.by(Sort.Direction.DESC, "lastModifiedAt"));
    }

    /** The rename was internal: a client sorting by {@code price} keeps working. */
    @Test
    void priceStillSortsByTheListPrice() {
        assertThat(propertyOf(ListingSort.parse("price,asc"))).isEqualTo("fullPrice");
        assertThat(propertyOf(ListingSort.parse("fullPrice,asc"))).isEqualTo("fullPrice");
    }

    @Test
    void sortsByTheDiscountPrice() {
        assertThat(propertyOf(ListingSort.parse("discountPrice,desc"))).isEqualTo("discountPrice");
    }

    @Test
    void readsTheDirection() {
        assertThat(directionOf(ListingSort.parse("price,desc"))).isEqualTo(Sort.Direction.DESC);
        assertThat(directionOf(ListingSort.parse("price"))).isEqualTo(Sort.Direction.ASC);
        assertThat(directionOf(ListingSort.parse("price, DESC "))).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void refusesAFieldThatIsNotOnTheAllowlist() {
        assertThatThrownBy(() -> ListingSort.parse("sellerProfile.suspensionReason"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot sort listings by");
    }

    @Test
    void refusesADirectionThatIsNeitherAscNorDesc() {
        assertThatThrownBy(() -> ListingSort.parse("price,sideways"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("asc");
    }
}
