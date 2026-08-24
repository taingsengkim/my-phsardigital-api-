package co.istad.projectpracticum.phsardigital.features.stock;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The only way stock changes. Callers hand over a signed delta and a reason; this keeps
 * {@code stock_qty} and the movement history in step and refreshes the sellable status.
 *
 * <p>Callers must already hold the listing's row lock — see
 * {@code ListingRepository.findByUuidForUpdate} — because the balance is read, checked
 * and written here as one decision.
 */
@Component
@RequiredArgsConstructor
public class StockLedger {

    private final StockMovementRepository stockMovementRepository;

    /**
     * Applies a signed change and records it.
     *
     * @param delta negative takes goods off the shelf, positive puts them back
     * @param ref   the order or counter sale behind it, or null for a manual change
     * @throws ResponseStatusException 409 when the change would drive stock negative
     */
    public StockMovement apply(Listing listing,
                               int delta,
                               StockMovementReason reason,
                               StockChannel channel,
                               UUID ref,
                               String note) {
        int current = listing.getStockQty() == null ? 0 : listing.getStockQty();
        long balance = (long) current + delta;
        if (balance < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough stock for: " + listing.getTitle()
                            + " (available " + current + ")");
        }
        if (balance > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock would overflow for: " + listing.getTitle());
        }

        int updated = (int) balance;
        listing.setStockQty(updated);
        applySoldCounter(listing, reason, delta);
        refreshStatus(listing, updated);

        StockMovement movement = new StockMovement();
        movement.setListing(listing);
        movement.setDelta(delta);
        movement.setBalanceAfter(updated);
        movement.setReason(reason);
        movement.setChannel(channel);
        movement.setRefUuid(ref);
        movement.setActorId(currentActor());
        movement.setNote(note);
        return stockMovementRepository.save(movement);
    }

    /**
     * Records a movement without changing the count, for stock that is already on the
     * listing — the opening balance of a newly created one.
     */
    public StockMovement record(Listing listing,
                                int delta,
                                StockMovementReason reason,
                                StockChannel channel,
                                UUID ref,
                                String note) {
        StockMovement movement = new StockMovement();
        movement.setListing(listing);
        movement.setDelta(delta);
        movement.setBalanceAfter(listing.getStockQty() == null ? 0 : listing.getStockQty());
        movement.setReason(reason);
        movement.setChannel(channel);
        movement.setRefUuid(ref);
        movement.setActorId(currentActor());
        movement.setNote(note);
        return stockMovementRepository.save(movement);
    }

    /** Whether this reference has already moved stock, so a retry does not move it twice. */
    public boolean alreadyApplied(UUID ref, StockMovementReason reason) {
        return ref != null && stockMovementRepository.existsByRefUuidAndReason(ref, reason);
    }

    /**
     * {@code sold} tracks goods that actually left against a sale, so a restock or a
     * recount leaves it alone.
     */
    private void applySoldCounter(Listing listing, StockMovementReason reason, int delta) {
        if (reason != StockMovementReason.SALE && reason != StockMovementReason.CANCEL) {
            return;
        }
        int sold = listing.getSold() == null ? 0 : listing.getSold();
        int updated = sold - delta;
        listing.setSold(Math.max(updated, 0));
    }

    /**
     * Stock depletion refines a sellable listing to SOLD_OUT and back, and never
     * overwrites a stronger moderation state.
     */
    private void refreshStatus(Listing listing, int stockQty) {
        if (stockQty == 0 && listing.getStatus() == ListingStatus.ACTIVE) {
            listing.setStatus(ListingStatus.SOLD_OUT);
        } else if (stockQty > 0 && listing.getStatus() == ListingStatus.SOLD_OUT) {
            listing.setStatus(ListingStatus.ACTIVE);
        }
    }

    private static String currentActor() {
        try {
            return AuthUtils.extractUserId();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
