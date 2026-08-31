package co.istad.projectpracticum.phsardigital.features.pos;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleLineRequest;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleRequest;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.*;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.stock.StockChannel;
import co.istad.projectpracticum.phsardigital.features.stock.StockLedger;
import co.istad.projectpracticum.phsardigital.features.stock.StockMovementReason;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosServiceImplTest {

    private static final String SELLER_ID = "seller-1";

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private PurchaseMapper purchaseMapper;
    @Mock
    private SellerAccessGuard sellerAccessGuard;
    @Mock
    private StockLedger stockLedger;
    @Mock
    private EntityManager entityManager;

    @Test
    void aCounterSaleCompletesImmediatelyAndTakesStockThroughTheLedger() {
        UUID saleUuid = UUID.randomUUID();
        Listing listing = listing("10.00");
        stubSale(saleUuid, listing);

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            service().sell(request(saleUuid, List.of(
                    new PosSaleLineRequest(listing.getUuid(), 2, null)), null));
        }

        ArgumentCaptor<Purchase> saved = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(saved.capture());
        Purchase sale = saved.getValue();

        assertThat(sale.getChannel()).isEqualTo(PurchaseChannel.POS);
        assertThat(sale.getStatus()).isEqualTo(PurchaseStatus.COMPLETED);
        // A walk-in has no account, so the order carries no buyer.
        assertThat(sale.getBuyerId()).isNull();
        assertThat(sale.getShippingAddress()).isNull();
        assertThat(sale.getTotalPrice()).isEqualByComparingTo("20.00");
        verify(stockLedger).apply(listing, -2, StockMovementReason.SALE,
                StockChannel.POS, saleUuid, null);
    }

    @Test
    void twoLinesForOneListingAreMergedSoItsRowIsLockedOnce() {
        UUID saleUuid = UUID.randomUUID();
        Listing listing = listing("10.00");
        stubSale(saleUuid, listing);

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            service().sell(request(saleUuid, List.of(
                    new PosSaleLineRequest(listing.getUuid(), 2, null),
                    new PosSaleLineRequest(listing.getUuid(), 3, null)), null));
        }

        verify(listingRepository).findByUuidForUpdate(listing.getUuid());
        verify(stockLedger).apply(listing, -5, StockMovementReason.SALE,
                StockChannel.POS, saleUuid, null);
    }

    @Test
    void resubmittingTheSameSaleReturnsTheOriginalWithoutSellingAgain() {
        UUID saleUuid = UUID.randomUUID();
        Purchase original = new Purchase();
        original.setUuid(saleUuid);
        original.setChannel(PurchaseChannel.POS);
        original.setStatus(PurchaseStatus.COMPLETED);
        original.setTotalPrice(new BigDecimal("20.00"));
        original.setSellerProfile(sellerProfile());
        when(purchaseRepository.findById(saleUuid)).thenReturn(Optional.of(original));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            service().sell(request(saleUuid, List.of(
                    new PosSaleLineRequest(UUID.randomUUID(), 2, null)), null));
        }

        verify(purchaseRepository, never()).save(any(Purchase.class));
        verify(purchaseMapper).toResponse(original);
    }

    @Test
    void changeIsReturnedForTheCashHandedOver() {
        UUID saleUuid = UUID.randomUUID();
        Listing listing = listing("10.00");
        stubSale(saleUuid, listing);

        PosSaleResponse response;
        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            response = service().sell(request(saleUuid, List.of(
                    new PosSaleLineRequest(listing.getUuid(), 2, null)),
                    new BigDecimal("50.00")));
        }

        assertThat(response.changeDue()).isEqualByComparingTo("30.00");
    }

    @Test
    void cashBelowTheTotalIsRefused() {
        UUID saleUuid = UUID.randomUUID();
        Listing listing = listing("10.00");
        stubSale(saleUuid, listing);

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThatThrownBy(() -> service().sell(request(saleUuid, List.of(
                    new PosSaleLineRequest(listing.getUuid(), 2, null)),
                    new BigDecimal("5.00"))))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST))
                    .hasMessageContaining("less than the total");
        }
    }

    @Test
    void theCounterMayDiscountButNotMarkUp() {
        UUID saleUuid = UUID.randomUUID();
        Listing listing = listing("10.00");
        stubLookups(saleUuid, listing);

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThatThrownBy(() -> service().sell(request(saleUuid, List.of(
                    new PosSaleLineRequest(listing.getUuid(), 1, new BigDecimal("12.00"))), null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("cannot exceed its list price");
        }

        verify(purchaseRepository, never()).save(any(Purchase.class));
    }

    @Test
    void sellingSomebodyElsesListingIsRefused() {
        UUID saleUuid = UUID.randomUUID();
        Listing listing = listing("10.00");
        listing.getSellerProfile().setSellerId("another-seller");
        when(purchaseRepository.findById(saleUuid)).thenReturn(Optional.empty());
        when(sellerAccessGuard.requireActiveSellerForTrade(SELLER_ID)).thenReturn(sellerProfile());
        when(listingRepository.findByUuidForUpdate(listing.getUuid()))
                .thenReturn(Optional.of(listing));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThatThrownBy(() -> service().sell(request(saleUuid, List.of(
                    new PosSaleLineRequest(listing.getUuid(), 1, null)), null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    /** Lookups only, for the paths that are refused before anything is written. */
    private void stubLookups(UUID saleUuid, Listing listing) {
        when(purchaseRepository.findById(saleUuid)).thenReturn(Optional.empty());
        when(sellerAccessGuard.requireActiveSellerForTrade(SELLER_ID)).thenReturn(sellerProfile());
        when(listingRepository.findByUuidForUpdate(listing.getUuid()))
                .thenReturn(Optional.of(listing));
    }

    private void stubSale(UUID saleUuid, Listing listing) {
        stubLookups(saleUuid, listing);
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PosServiceImpl service() {
        // Null payment service: every sale here is cash, which never opens a payment.
        return new PosServiceImpl(purchaseRepository, listingRepository, purchaseMapper,
                sellerAccessGuard, stockLedger, entityManager, null);
    }

    private static MockedStatic<AuthUtils> authenticatedSeller() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(SELLER_ID);
        return auth;
    }

    private static PosSaleRequest request(UUID saleUuid, List<PosSaleLineRequest> lines,
                                          BigDecimal tendered) {
        // Payment method left null on purpose: the counter defaults to cash, and these
        // tests are about stock and money rather than about how it was handed over.
        return new PosSaleRequest(saleUuid, lines, "Walk-in", null, null, tendered, null, null);
    }

    private static SellerProfile sellerProfile() {
        SellerProfile seller = new SellerProfile(SELLER_ID);
        seller.setIsActive(true);
        return seller;
    }

    private static Listing listing(String fullPrice) {
        Listing listing = new Listing();
        listing.setUuid(UUID.randomUUID());
        listing.setTitle("Phone");
        listing.setFullPrice(new BigDecimal(fullPrice));
        listing.setStockQty(10);
        listing.setSold(0);
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setSellerProfile(sellerProfile());
        return listing;
    }
}
