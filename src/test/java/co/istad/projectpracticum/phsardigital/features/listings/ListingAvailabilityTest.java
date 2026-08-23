package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingAvailabilityTest {

    @Mock
    private CategoryAvailability categoryAvailability;

    private ListingAvailability availability;
    private Category category;
    private SellerProfile seller;
    private Listing listing;

    @BeforeEach
    void setUp() {
        availability = new ListingAvailability(categoryAvailability);
        category = new Category();
        seller = new SellerProfile("seller-1");
        seller.setIsActive(true);

        listing = new Listing();
        listing.setTitle("Phone");
        listing.setCategory(category);
        listing.setSellerProfile(seller);
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setStockQty(1);
    }

    @Test
    void activeListingNeedsAnActiveSellerAndAvailableCategory() {
        when(categoryAvailability.isEffectivelyActive(category)).thenReturn(true);
        assertThat(availability.isPubliclyVisible(listing)).isTrue();
        assertThat(availability.isBuyable(listing)).isTrue();

        seller.setIsActive(false);
        assertThat(availability.isPubliclyVisible(listing)).isFalse();
        assertThat(availability.isBuyable(listing)).isFalse();
    }

    @Test
    void soldOutListingMayRemainVisibleButCannotBeBought() {
        listing.setStatus(ListingStatus.SOLD_OUT);
        when(categoryAvailability.isEffectivelyActive(category)).thenReturn(true);

        assertThat(availability.isPubliclyVisible(listing)).isTrue();
        assertThat(availability.isBuyable(listing)).isFalse();
        assertThatThrownBy(() -> availability.requireBuyable(listing))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void inactiveAncestorMakesAListingUnavailable() {
        when(categoryAvailability.isEffectivelyActive(category)).thenReturn(false);

        assertThat(availability.isPubliclyVisible(listing)).isFalse();
        assertThat(availability.isBuyable(listing)).isFalse();
    }

    @Test
    void activeStatusAloneDoesNotMakeZeroStockBuyable() {
        listing.setStockQty(0);
        when(categoryAvailability.isEffectivelyActive(category)).thenReturn(true);

        assertThat(availability.isPubliclyVisible(listing)).isTrue();
        assertThat(availability.isBuyable(listing)).isFalse();
    }
}
