package co.istad.projectpracticum.phsardigital.features.listings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * How a listing gets the slug its public URL is built from.
 *
 * <p>Slugs are unique across the whole table, and they are derived from the title rather
 * than typed, so a second shop listing "iPhone 15 Pro" had nothing to correct when the
 * create failed with {@code 409 Listing slug already exists} — the only way out was to
 * rename a product that was named correctly. The collision is settled with a suffix
 * instead.
 */
@ExtendWith(MockitoExtension.class)
class ListingSlugTest {

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ListingServiceImpl listingService;

    private void slugsInUse(String base, UUID excludeUuid, String... taken) {
        when(listingRepository.findSlugsFrom(base, excludeUuid)).thenReturn(List.of(taken));
    }

    @Test
    void aFreeTitleKeepsThePlainSlug() {
        slugsInUse("iphone-15-pro", null);

        assertThat(listingService.uniqueSlug("iPhone 15 Pro", null)).isEqualTo("iphone-15-pro");
    }

    @Test
    void aTitleAnotherShopAlreadyListsGetsASuffixRatherThanAConflict() {
        slugsInUse("iphone-15-pro", null, "iphone-15-pro");

        assertThat(listingService.uniqueSlug("iPhone 15 Pro", null)).isEqualTo("iphone-15-pro-2");
    }

    @Test
    void theSuffixCountsPastEveryOneAlreadyTaken() {
        slugsInUse("iphone-15-pro", null,
                "iphone-15-pro", "iphone-15-pro-2", "iphone-15-pro-3");

        assertThat(listingService.uniqueSlug("iPhone 15 Pro", null)).isEqualTo("iphone-15-pro-4");
    }

    @Test
    void aGapLeftByADeletedListingIsFilled() {
        slugsInUse("iphone-15-pro", null, "iphone-15-pro", "iphone-15-pro-3");

        assertThat(listingService.uniqueSlug("iPhone 15 Pro", null)).isEqualTo("iphone-15-pro-2");
    }

    /**
     * The query matches on a prefix, so it hands back slugs belonging to quite different
     * products. Those are not collisions and must not push the suffix along.
     */
    @Test
    void aLongerNeighbourIsNotACollision() {
        slugsInUse("iphone-15-pro", null, "iphone-15-pro-max", "iphone-15-pro-max-2");

        assertThat(listingService.uniqueSlug("iPhone 15 Pro", null)).isEqualTo("iphone-15-pro");
    }

    /**
     * A listing being renamed excludes itself from the query, so re-deriving its slug
     * gives back the one its links already point at rather than stepping past it.
     */
    @Test
    void aListingDoesNotHaveToDodgeItsOwnSlug() {
        UUID renaming = UUID.randomUUID();
        slugsInUse("iphone-15-pro", renaming, "iphone-15-pro");

        assertThat(listingService.uniqueSlug("iPhone 15 Pro", renaming))
                .isEqualTo("iphone-15-pro-2");
    }
}
