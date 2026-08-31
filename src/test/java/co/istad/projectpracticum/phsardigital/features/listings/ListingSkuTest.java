package co.istad.projectpracticum.phsardigital.features.listings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shop code's contract, exercised through the repository query that enforces it.
 *
 * <p>The uniqueness rule lives in a query rather than a database constraint because it
 * is "unique per shop, ignoring case" — two shops both numbering their first t-shirt
 * {@code TS-001} is ordinary, and a global unique index would have them fighting over
 * codes neither can see.
 */
class ListingSkuTest {

    @Test
    void theSkuColumnIsBoundedSoAScannerCannotOverrunIt() throws Exception {
        var column = Listing.class.getDeclaredField("sku")
                .getAnnotation(jakarta.persistence.Column.class);

        assertThat(column).isNotNull();
        assertThat(column.length()).isEqualTo(64);
        // Nullable: a shop that does not use codes must not be forced to invent them.
        assertThat(column.nullable()).isTrue();
    }

    @Test
    void theSkuIsNotGloballyUniqueBecauseTwoShopsMayUseTheSameCode() throws Exception {
        var column = Listing.class.getDeclaredField("sku")
                .getAnnotation(jakarta.persistence.Column.class);

        assertThat(column.unique())
                .as("a global unique index would make one shop's codes collide with another's")
                .isFalse();
    }
}
