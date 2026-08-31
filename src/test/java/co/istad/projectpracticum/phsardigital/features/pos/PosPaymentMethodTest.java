package co.istad.projectpracticum.phsardigital.features.pos;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleLineRequest;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleRequest;
import co.istad.projectpracticum.phsardigital.features.purchases.PaymentMethod;
import co.istad.projectpracticum.phsardigital.features.purchases.Purchase;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseMapper;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.stock.StockLedger;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class PosPaymentMethodTest {

    private static final String SELLER_ID = "seller-1";

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private PurchaseMapper purchaseMapper;
    @Mock private SellerAccessGuard sellerAccessGuard;
    @Mock private StockLedger stockLedger;
    @Mock private EntityManager entityManager;

    private PosServiceImpl service;
    private UUID listingUuid;

    @BeforeEach
    void setUp() {
        service = new PosServiceImpl(purchaseRepository, listingRepository, purchaseMapper,
                sellerAccessGuard, stockLedger, entityManager);

        SellerProfile seller = new SellerProfile(SELLER_ID);
        seller.setIsActive(true);
        when(sellerAccessGuard.requireActiveSellerForTrade(SELLER_ID)).thenReturn(seller);

        listingUuid = UUID.randomUUID();
        Listing listing = new Listing();
        listing.setUuid(listingUuid);
        listing.setTitle("Iced Coffee");
        listing.setFullPrice(new BigDecimal("2.50"));
        listing.setStockQty(100);
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setSellerProfile(seller);

        when(listingRepository.findByUuidForUpdate(listingUuid)).thenReturn(Optional.of(listing));
        when(purchaseRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(c -> c.getArgument(0));
    }

    @Test
    void aTillThatSaysNothingIsTakingCash() {
        // Keeps every till written before payment methods existed working unchanged.
        Purchase sale = sell(request(null, new BigDecimal("5.00")));
        assertThat(sale.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void aKhqrSaleIsRecordedAsSuchAndNeedsNoChangeCalculated() {
        Purchase sale = sell(request(PaymentMethod.KHQR, null));

        assertThat(sale.getPaymentMethod()).isEqualTo(PaymentMethod.KHQR);
    }

    @Test
    void cashTenderedOnAKhqrSaleIsRefusedRatherThanPrintingChangeNobodyGave() {
        // The customer transferred an exact amount, so there is no float to give change
        // from. Silently ignoring it would send them away short.
        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThatThrownBy(() -> service.sell(request(PaymentMethod.KHQR, new BigDecimal("10.00"))))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST))
                    .hasMessageContaining("KHQR");
        }
    }

    @Test
    void cashBelowTheTotalIsStillRefused() {
        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThatThrownBy(() -> service.sell(request(PaymentMethod.CASH, new BigDecimal("1.00"))))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void changeIsTheDifferenceAndIsReportedAlongsideTheMethod() {
        UUID saleUuid = UUID.randomUUID();
        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var response = service.sell(new PosSaleRequest(saleUuid,
                    List.of(new PosSaleLineRequest(listingUuid, 2, null)),
                    "Walk-in", null, PaymentMethod.CASH, new BigDecimal("10.00"), null, null));

            // 2 x 2.50 = 5.00 tendered 10.00
            assertThat(response.changeDue()).isEqualByComparingTo("5.00");
            assertThat(response.amountTendered()).isEqualByComparingTo("10.00");
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        }
    }

    @Test
    void aRetriedSaleKeepsTheMethodItWasFirstPaidWith() {
        // The till resends after a dropped connection. What the customer actually paid
        // with was settled the first time; a retry that disagrees does not rewrite it.
        UUID saleUuid = UUID.randomUUID();
        Purchase original = new Purchase();
        original.setUuid(saleUuid);
        original.setChannel(co.istad.projectpracticum.phsardigital.features.purchases.PurchaseChannel.POS);
        original.setSellerProfile(new SellerProfile(SELLER_ID));
        original.setTotalPrice(new BigDecimal("5.00"));
        original.setPaymentMethod(PaymentMethod.KHQR);
        when(purchaseRepository.findById(saleUuid)).thenReturn(Optional.of(original));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var response = service.sell(new PosSaleRequest(saleUuid,
                    List.of(new PosSaleLineRequest(listingUuid, 2, null)),
                    "Walk-in", null, PaymentMethod.CASH, null, null, null));

            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.KHQR);
        }
    }

    private Purchase sell(PosSaleRequest request) {
        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            service.sell(request);
        }
        ArgumentCaptor<Purchase> saved = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(saved.capture());
        return saved.getValue();
    }

    private PosSaleRequest request(PaymentMethod method, BigDecimal tendered) {
        return new PosSaleRequest(UUID.randomUUID(),
                List.of(new PosSaleLineRequest(listingUuid, 2, null)),
                "Walk-in", null, method, tendered, null, null);
    }

    private static MockedStatic<AuthUtils> authenticatedSeller() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(SELLER_ID);
        return auth;
    }
}
