package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.favorites.FavoriteRepository;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The buying price is the one field on a listing that must never reach a stranger: a
 * competitor who knows it knows exactly how far they can undercut the shop.
 *
 * <p>{@code ListingMapper} is generated with {@code costPrice} hard-coded to null, so
 * the default everywhere is that it is absent. These tests cover the only place that
 * puts it back.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListingCostPriceDisclosureTest {

    private static final String OWNER = "seller-1";
    private static final BigDecimal COST = new BigDecimal("12.00");

    @Mock private ListingMapper listingMapper;
    @Mock private ReviewRepository reviewRepository;
    @Mock private FavoriteRepository favoriteRepository;

    private ListingResponseFactory factory;
    private Listing listing;

    @BeforeEach
    void setUp() {
        factory = new ListingResponseFactory(listingMapper, reviewRepository, favoriteRepository);

        SellerProfile shop = new SellerProfile(OWNER);
        listing = new Listing();
        listing.setUuid(UUID.randomUUID());
        listing.setTitle("Iced Coffee");
        listing.setFullPrice(new BigDecimal("2.50"));
        listing.setCostPrice(COST);
        listing.setSellerProfile(shop);

        // As the real mapper does: the field is never read off the entity.
        when(listingMapper.toResponse(any(Listing.class))).thenAnswer(call -> bareResponse());
    }

    @Test
    void theShopThatOwnsTheListingSeesWhatItPaid() {
        try (MockedStatic<AuthUtils> auth = signedInAs(OWNER, false)) {
            assertThat(factory.one(listing).costPrice()).isEqualByComparingTo(COST);
        }
    }

    @Test
    void anotherSellerDoesNot() {
        // The case that matters: a rival browsing the catalogue must not be able to read
        // the margin off a competitor's product.
        try (MockedStatic<AuthUtils> auth = signedInAs("seller-2", false)) {
            assertThat(factory.one(listing).costPrice()).isNull();
        }
    }

    @Test
    void anAnonymousShopperDoesNot() {
        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::isAuthenticated).thenReturn(false);
            assertThat(factory.one(listing).costPrice()).isNull();
        }
    }

    @Test
    void anAdminDoesBecauseModeratingAShopMeansSeeingWhatItRecorded() {
        try (MockedStatic<AuthUtils> auth = signedInAs("admin-1", true)) {
            assertThat(factory.one(listing).costPrice()).isEqualByComparingTo(COST);
        }
    }

    @Test
    void aListingWithNoRecordedCostAnswersNullForItsOwnerToo() {
        listing.setCostPrice(null);
        try (MockedStatic<AuthUtils> auth = signedInAs(OWNER, false)) {
            assertThat(factory.one(listing).costPrice()).isNull();
        }
    }

    private static MockedStatic<AuthUtils> signedInAs(String userId, boolean admin) {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::isAuthenticated).thenReturn(true);
        auth.when(AuthUtils::extractUserId).thenReturn(userId);
        auth.when(() -> AuthUtils.hasRole("ADMIN")).thenReturn(admin);
        return auth;
    }

    /** What the real mapper produces: everything except the cost. */
    private static ListingResponse bareResponse() {
        return new ListingResponse(
                UUID.randomUUID(), null, null, "Iced Coffee", "iced-coffee", null,
                "SKU-1", null,
                new BigDecimal("2.50"), null, 10,
                ListingStatus.ACTIVE, false, null, 0, null, null, null,
                null, null, null, 0L, false);
    }
}
