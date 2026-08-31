package co.istad.projectpracticum.phsardigital.features.pos;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.payments.Payment;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentPurpose;
import co.istad.projectpracticum.phsardigital.features.purchases.Purchase;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseItem;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.stock.StockChannel;
import co.istad.projectpracticum.phsardigital.features.stock.StockLedger;
import co.istad.projectpracticum.phsardigital.features.stock.StockMovementReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosPaymentSettlementTest {

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private StockLedger stockLedger;

    private PosPaymentSettlement settlement;
    private Purchase sale;
    private Listing listing;
    private Payment payment;

    @BeforeEach
    void setUp() {
        settlement = new PosPaymentSettlement(purchaseRepository, listingRepository, stockLedger);

        listing = new Listing();
        listing.setUuid(UUID.randomUUID());
        listing.setTitle("Iced Coffee");
        listing.setStockQty(98);

        sale = new Purchase();
        sale.setUuid(UUID.randomUUID());
        sale.setStatus(PurchaseStatus.PENDING);

        PurchaseItem item = new PurchaseItem();
        item.setPurchase(sale);
        item.setListing(listing);
        item.setQuantity(2);
        sale.getItems().add(item);

        payment = new Payment();
        payment.setUuid(UUID.randomUUID());
        payment.setPurpose(PaymentPurpose.POS_SALE);
        payment.setReference(sale.getUuid().toString());

        when(purchaseRepository.findByUuidForUpdate(sale.getUuid())).thenReturn(Optional.of(sale));
        when(listingRepository.findByUuidForUpdate(listing.getUuid())).thenReturn(Optional.of(listing));
    }

    @Test
    void aConfirmedTransferCompletesTheSale() {
        settlement.settle(payment);

        assertThat(sale.getStatus()).isEqualTo(PurchaseStatus.COMPLETED);
        assertThat(sale.getCompletedAt()).isNotNull();
        verify(purchaseRepository).save(sale);
    }

    @Test
    void settlingTwiceDoesNotTouchAnAlreadyCompletedSale() {
        sale.setStatus(PurchaseStatus.COMPLETED);

        settlement.settle(payment);

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void anAbandonedQrGivesTheStockBack() {
        // Reached only after Bakong has confirmed no such transfer exists, so this is a
        // customer who walked away rather than one whose payment is merely slow.
        settlement.onExpired(payment);

        verify(stockLedger).apply(eq(listing), eq(2), eq(StockMovementReason.CANCEL),
                eq(StockChannel.POS), eq(sale.getUuid()), any());
        assertThat(sale.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(sale.getCancelledAt()).isNotNull();
    }

    @Test
    void expiringASaleThatIsNoLongerPendingReturnsTheStockOnlyOnce() {
        sale.setStatus(PurchaseStatus.CANCELLED);

        settlement.onExpired(payment);

        verify(stockLedger, never()).apply(any(), org.mockito.ArgumentMatchers.anyInt(),
                any(), any(), any(), any());
    }

    @Test
    void aPaymentArrivingAfterTheSaleWasWrittenOffIsRaisedRatherThanSwallowed() {
        // The stock went back and may since have been sold again, so completing this now
        // would sell goods twice. The money is real, so it cannot be ignored either.
        sale.setStatus(PurchaseStatus.CANCELLED);

        assertThatThrownBy(() -> settlement.settle(payment))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(sale.getUuid().toString());

        verify(purchaseRepository, never()).save(any());
    }
}
