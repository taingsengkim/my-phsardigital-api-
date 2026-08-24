package co.istad.projectpracticum.phsardigital.features.pos;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
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
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

    private final PurchaseRepository purchaseRepository;
    private final ListingRepository listingRepository;
    private final PurchaseMapper purchaseMapper;
    private final SellerAccessGuard sellerAccessGuard;
    private final StockLedger stockLedger;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public PosSaleResponse sell(PosSaleRequest request) {
        String sellerId = AuthUtils.extractUserId();

        // A till that lost its connection resends the same sale. Answering with the
        // original is the whole point of letting it choose the id.
        Purchase existing = purchaseRepository.findById(request.saleUuid()).orElse(null);
        if (existing != null) {
            return replay(existing, sellerId, request);
        }

        SellerProfile seller = sellerAccessGuard.requireActiveSellerForTrade(sellerId);

        Purchase sale = new Purchase();
        sale.setUuid(request.saleUuid());
        sale.setSellerProfile(seller);
        sale.setChannel(PurchaseChannel.POS);
        // A counter sale is done when it is rung up: the goods are already in the
        // customer's hands, so there is no pending state to move through.
        sale.setStatus(PurchaseStatus.COMPLETED);
        sale.setBuyerId(null);
        sale.setRecipientName(trimToNull(request.customerName()));
        sale.setRecipientPhone(trimToNull(request.customerPhone()));
        sale.setNote(trimToNull(request.note()));

        BigDecimal total = Money.ZERO;
        for (PosSaleLineRequest line : mergedLines(request.lines())) {
            Listing listing = lockOwnedListing(line.listingUuid(), sellerId);
            BigDecimal unitPrice = priceFor(listing, line.unitPrice());

            stockLedger.apply(listing, -line.quantity(), StockMovementReason.SALE,
                    StockChannel.POS, sale.getUuid(), null);
            listingRepository.save(listing);

            PurchaseItem item = new PurchaseItem();
            item.setPurchase(sale);
            item.setListing(listing);
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setUnitFullPrice(Money.of(listing.getFullPrice()));
            sale.getItems().add(item);

            total = Money.add(total, Money.multiply(unitPrice, line.quantity()));
        }
        sale.setTotalPrice(total);
        applySoldAt(sale, request.soldAt());

        Purchase saved = purchaseRepository.save(sale);
        return respond(saved, request.amountTendered());
    }

    /**
     * Lines are merged by listing so one listing is locked once. Two lines for the same
     * product would otherwise take the same row lock twice and check stock against a
     * balance the first line had already moved.
     */
    private List<PosSaleLineRequest> mergedLines(List<PosSaleLineRequest> lines) {
        Map<UUID, PosSaleLineRequest> merged = new LinkedHashMap<>();
        for (PosSaleLineRequest line : lines) {
            merged.merge(line.listingUuid(), line, (first, second) -> new PosSaleLineRequest(
                    first.listingUuid(),
                    first.quantity() + second.quantity(),
                    first.unitPrice() != null ? first.unitPrice() : second.unitPrice()));
        }
        // Sorted by id, so concurrent sales take their row locks in the same sequence.
        return merged.values().stream()
                .sorted(Comparator.comparing(PosSaleLineRequest::listingUuid))
                .toList();
    }

    private Listing lockOwnedListing(UUID listingUuid, String sellerId) {
        Listing listing = listingRepository.findByUuidForUpdate(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Listing not found: " + listingUuid));
        entityManager.refresh(listing, LockModeType.PESSIMISTIC_WRITE);
        if (!listing.getSellerProfile().getSellerId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only sell your own listings at your counter.");
        }
        return listing;
    }

    /**
     * The counter may discount but not mark up: a price above the catalogue's would let
     * the till quietly charge more than the listing advertises.
     */
    private BigDecimal priceFor(Listing listing, BigDecimal override) {
        if (override == null) {
            return Money.of(listing.effectivePrice());
        }
        if (Money.isNegative(override)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A line price cannot be negative.");
        }
        BigDecimal listPrice = listing.getFullPrice();
        if (listPrice != null && override.compareTo(listPrice) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The counter price for " + listing.getTitle()
                            + " cannot exceed its list price of " + listPrice + ".");
        }
        return Money.of(override);
    }

    /** A queued offline sale keeps its own time; a future one is refused as a bad clock. */
    private void applySoldAt(Purchase sale, LocalDateTime soldAt) {
        if (soldAt == null) {
            return;
        }
        if (soldAt.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A sale cannot be recorded in the future. Check the till's clock.");
        }
        sale.setCreatedAt(soldAt);
    }

    private PosSaleResponse replay(Purchase existing, String sellerId, PosSaleRequest request) {
        if (existing.getChannel() != PurchaseChannel.POS
                || !existing.getSellerProfile().getSellerId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This sale UUID was already used for a different sale.");
        }
        return respond(existing, request.amountTendered());
    }

    private PosSaleResponse respond(Purchase sale, BigDecimal amountTendered) {
        BigDecimal tendered = Money.of(amountTendered);
        BigDecimal change = null;
        if (tendered != null) {
            if (Money.isLessThan(tendered, sale.getTotalPrice())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The amount tendered is less than the total of " + sale.getTotalPrice() + ".");
            }
            change = Money.subtract(tendered, sale.getTotalPrice());
        }
        return new PosSaleResponse(purchaseMapper.toResponse(sale), tendered, change);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
