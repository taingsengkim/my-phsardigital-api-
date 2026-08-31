package co.istad.projectpracticum.phsardigital.features.pos;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.payments.Payment;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentPurpose;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentSettlement;
import co.istad.projectpracticum.phsardigital.features.purchases.Purchase;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseItem;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.stock.StockChannel;
import co.istad.projectpracticum.phsardigital.features.stock.StockLedger;
import co.istad.projectpracticum.phsardigital.features.stock.StockMovementReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Finishes a counter sale once Bakong confirms the customer's transfer, and unwinds it
 * when they walk away without paying.
 *
 * <p>A QR sale takes its stock at the moment it is rung up, the same as a cash one — the
 * cashier has the goods on the counter and nobody else may be sold them while the
 * customer is scanning. What it does not do is call itself complete: that waits for the
 * money, because a counter sale marked complete is a sale the shop believes it was paid
 * for.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PosPaymentSettlement implements PaymentSettlement {

    private final PurchaseRepository purchaseRepository;
    private final ListingRepository listingRepository;
    private final StockLedger stockLedger;

    @Override
    public PaymentPurpose purpose() {
        return PaymentPurpose.POS_SALE;
    }

    @Override
    public void settle(Payment payment) {
        Purchase sale = requireSale(payment);

        // Already finished, by an earlier poll or by the sweeper reaching it first.
        if (sale.getStatus() == PurchaseStatus.COMPLETED) {
            return;
        }
        if (sale.getStatus() == PurchaseStatus.CANCELLED) {
            // Written off as abandoned, and then paid. The stock has already gone back,
            // so completing it now would sell goods the shop may since have sold again.
            // The money is real, so this must be looked at rather than swallowed.
            log.error("Payment {} settled for counter sale {}, which had already been "
                            + "cancelled and its stock returned. The customer has paid.",
                    payment.getUuid(), sale.getUuid());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This sale was cancelled before the payment arrived. Contact support "
                            + "quoting " + sale.getUuid() + ".");
        }

        sale.setStatus(PurchaseStatus.COMPLETED);
        sale.setCompletedAt(LocalDateTime.now());
        purchaseRepository.save(sale);
        log.info("Counter sale {} completed by KHQR payment {}", sale.getUuid(), payment.getUuid());
    }

    /**
     * Puts the goods back on the shelf.
     *
     * <p>Reached only after Bakong has confirmed no such transfer exists — the sweeper
     * never writes a payment off on the clock alone — so this is a customer who changed
     * their mind, not one whose payment is merely slow.
     */
    @Override
    public void onExpired(Payment payment) {
        Purchase sale = purchaseRepository.findByUuidForUpdate(saleUuid(payment)).orElse(null);
        if (sale == null || sale.getStatus() != PurchaseStatus.PENDING) {
            return;
        }

        for (PurchaseItem item : lockOrdered(sale)) {
            Listing listing = listingRepository.findByUuidForUpdate(item.getListing().getUuid())
                    .orElse(null);
            if (listing == null) {
                continue;
            }
            stockLedger.apply(listing, item.getQuantity(), StockMovementReason.CANCEL,
                    StockChannel.POS, sale.getUuid(), null);
            listingRepository.save(listing);
        }

        sale.setStatus(PurchaseStatus.CANCELLED);
        sale.setCancelledAt(LocalDateTime.now());
        purchaseRepository.save(sale);
        log.info("Counter sale {} abandoned unpaid; stock returned", sale.getUuid());
    }

    private Purchase requireSale(Payment payment) {
        return purchaseRepository.findByUuidForUpdate(saleUuid(payment))
                .orElseThrow(() -> {
                    log.error("Payment {} names counter sale {}, which does not exist",
                            payment.getUuid(), payment.getReference());
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "The sale this payment belongs to is missing.");
                });
    }

    private static UUID saleUuid(Payment payment) {
        return UUID.fromString(payment.getReference());
    }

    /** Same ordering as everywhere else that locks listings, so nothing deadlocks. */
    private static List<PurchaseItem> lockOrdered(Purchase sale) {
        return sale.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getListing().getUuid()))
                .toList();
    }
}
