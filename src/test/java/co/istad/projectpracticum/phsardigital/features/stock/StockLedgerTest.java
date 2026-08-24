package co.istad.projectpracticum.phsardigital.features.stock;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLedgerTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    private StockLedger ledger() {
        return new StockLedger(stockMovementRepository);
    }

    @Test
    void aSaleTakesStockRaisesSoldAndRecordsTheMovement() {
        Listing listing = listing(5, 0, ListingStatus.ACTIVE);
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockMovement movement = ledger().apply(listing, -2, StockMovementReason.SALE,
                StockChannel.POS, null, null);

        assertThat(listing.getStockQty()).isEqualTo(3);
        assertThat(listing.getSold()).isEqualTo(2);
        assertThat(movement.getDelta()).isEqualTo(-2);
        assertThat(movement.getBalanceAfter()).isEqualTo(3);
        assertThat(movement.getChannel()).isEqualTo(StockChannel.POS);
    }

    @Test
    void sellingTheLastUnitMarksAnActiveListingSoldOut() {
        Listing listing = listing(1, 0, ListingStatus.ACTIVE);
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ledger().apply(listing, -1, StockMovementReason.SALE, StockChannel.ONLINE, null, null);

        assertThat(listing.getStockQty()).isZero();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD_OUT);
    }

    @Test
    void restoringStockBringsASoldOutListingBack() {
        Listing listing = listing(0, 1, ListingStatus.SOLD_OUT);
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ledger().apply(listing, 1, StockMovementReason.CANCEL, StockChannel.ONLINE, null, null);

        assertThat(listing.getStockQty()).isEqualTo(1);
        assertThat(listing.getSold()).isZero();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void inventoryAccountingNeverOverwritesAStrongerModerationState() {
        Listing listing = listing(1, 0, ListingStatus.SUSPENDED);
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ledger().apply(listing, -1, StockMovementReason.SALE, StockChannel.ONLINE, null, null);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
    }

    @Test
    void aChangeThatWouldGoNegativeIsRefusedAndRecordsNothing() {
        Listing listing = listing(1, 0, ListingStatus.ACTIVE);

        assertThatThrownBy(() -> ledger().apply(listing, -2, StockMovementReason.SALE,
                StockChannel.POS, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("available 1");

        assertThat(listing.getStockQty()).isEqualTo(1);
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void aRestockLeavesTheSoldCounterAlone() {
        Listing listing = listing(2, 7, ListingStatus.ACTIVE);
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ledger().apply(listing, 5, StockMovementReason.RESTOCK, StockChannel.MANUAL, null, null);

        assertThat(listing.getStockQty()).isEqualTo(7);
        assertThat(listing.getSold()).isEqualTo(7);
    }

    private static Listing listing(int stockQty, int sold, ListingStatus status) {
        Listing listing = new Listing();
        listing.setUuid(UUID.randomUUID());
        listing.setTitle("Phone");
        listing.setStockQty(stockQty);
        listing.setSold(sold);
        listing.setStatus(status);
        return listing;
    }
}
