package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.address.AddressService;
import co.istad.projectpracticum.phsardigital.features.cart.CartRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingAvailability;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.stock.StockLedger;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerOrderDashboardTest {

    private static final String SELLER = "seller-1";

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private PurchaseMapper purchaseMapper;
    @Mock private SellerAccessGuard sellerAccessGuard;
    @Mock private AddressService addressService;
    @Mock private ListingAvailability listingAvailability;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private EntityManager entityManager;
    @Mock private StockLedger stockLedger;

    private PurchaseServiceImpl service() {
        return new PurchaseServiceImpl(purchaseRepository, cartRepository, listingRepository,
                purchaseMapper, sellerAccessGuard, addressService, listingAvailability,
                userProfileRepository, entityManager, stockLedger);
    }

    // ---------- summary ----------

    @Test
    void theSummaryRollsUpEveryStateFromOneQuery() {
        when(purchaseRepository.summariseOrdersForSeller(SELLER)).thenReturn(List.of(
                new Object[]{"PENDING", 3L, new BigDecimal("450.00")},
                new Object[]{"CONFIRMED", 2L, new BigDecimal("300.00")},
                new Object[]{"COMPLETED", 10L, new BigDecimal("1750.50")},
                new Object[]{"CANCELLED", 1L, new BigDecimal("99.00")}
        ));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var summary = service().summariseMyOrders();

            assertThat(summary.pending()).isEqualTo(3);
            assertThat(summary.confirmed()).isEqualTo(2);
            assertThat(summary.completed()).isEqualTo(10);
            assertThat(summary.cancelled()).isEqualTo(1);
            assertThat(summary.total()).isEqualTo(16);
            // Delivered money and promised money stay apart: adding them would
            // overstate what the shop has actually taken.
            assertThat(summary.earnedRevenue()).isEqualByComparingTo("1750.50");
            assertThat(summary.inFlightRevenue()).isEqualByComparingTo("300.00");
        }
    }

    @Test
    void aStateWithNoOrdersReadsAsZeroRatherThanMissing() {
        // The query returns no row at all for a state nobody has reached, and a
        // dashboard tile still has to render something.
        when(purchaseRepository.summariseOrdersForSeller(SELLER)).thenReturn(List.<Object[]>of(
                new Object[]{"PENDING", 2L, new BigDecimal("120.00")}
        ));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var summary = service().summariseMyOrders();

            assertThat(summary.pending()).isEqualTo(2);
            assertThat(summary.completed()).isZero();
            assertThat(summary.total()).isEqualTo(2);
            assertThat(summary.earnedRevenue()).isEqualByComparingTo("0.00");
            assertThat(summary.inFlightRevenue()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    void aShopWithNoOrdersAtAllStillAnswers() {
        when(purchaseRepository.summariseOrdersForSeller(SELLER)).thenReturn(List.of());

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var summary = service().summariseMyOrders();

            assertThat(summary.total()).isZero();
            assertThat(summary.earnedRevenue()).isEqualByComparingTo("0.00");
        }
    }

    // ---------- search ----------

    @Test
    void aBlankSearchBoxFallsThroughToThePlainListing() {
        // A blank term would otherwise run the item and listing joins to match everything.
        when(purchaseRepository.findBySellerProfile_SellerId(eq(SELLER), any(Pageable.class)))
                .thenReturn(Page.empty());

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            service().findSellerOrders(null, "   ", 0, 20);
        }

        verify(purchaseRepository).findBySellerProfile_SellerId(eq(SELLER), any(Pageable.class));
        verify(purchaseRepository, never()).searchSellerOrders(any(), any(), any(), any());
    }

    @Test
    void searchingWithoutAStatusFilterCoversEveryState() {
        // The status set is passed explicitly rather than as a null parameter, so
        // Hibernate always has a type to infer.
        when(purchaseRepository.searchSellerOrders(eq(SELLER), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            service().findSellerOrders(null, "Dara", 0, 20);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<PurchaseStatus>> statuses =
                ArgumentCaptor.forClass(Collection.class);
        verify(purchaseRepository).searchSellerOrders(
                eq(SELLER), statuses.capture(), eq("%dara%"), any(Pageable.class));
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrderElementsOf(Set.of(PurchaseStatus.values()));
    }

    @Test
    void searchingInsideATabNarrowsToThatOneState() {
        when(purchaseRepository.searchSellerOrders(eq(SELLER), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            service().findSellerOrders(PurchaseStatus.PENDING, "Dara", 0, 20);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<PurchaseStatus>> statuses =
                ArgumentCaptor.forClass(Collection.class);
        verify(purchaseRepository).searchSellerOrders(
                eq(SELLER), statuses.capture(), eq("%dara%"), any(Pageable.class));
        assertThat(statuses.getValue()).containsExactly(PurchaseStatus.PENDING);
    }

    @Test
    void anOrderReferencePastedAsShownStillMatches() {
        // Sellers see "#ORD-9f3a…" but the column holds a bare UUID.
        when(purchaseRepository.searchSellerOrders(eq(SELLER), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            service().findSellerOrders(null, "#ORD-9F3A", 0, 20);
        }

        verify(purchaseRepository).searchSellerOrders(
                eq(SELLER), any(), eq("%9f3a%"), any(Pageable.class));
    }

    private static MockedStatic<AuthUtils> authenticatedSeller() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(SELLER);
        return auth;
    }
}
