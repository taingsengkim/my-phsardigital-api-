package co.istad.projectpracticum.phsardigital.features.pos;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleLineRequest;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleRequest;
import co.istad.projectpracticum.phsardigital.features.payments.Payment;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentCurrency;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentPurpose;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentService;
import co.istad.projectpracticum.phsardigital.features.payments.dto.PaymentResponse;
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
    private final PaymentService paymentService;
    private final co.istad.projectpracticum.phsardigital.features.seller.SellerRepository sellerRepository;

    @Override
    @Transactional
    public PosSaleResponse sell(PosSaleRequest request) {
        String sellerId = AuthUtils.extractUserId();

        // Serialize the till before looking for the sale. Recognising a resend is a read
        // followed by an insert, and without a lock between them two submissions a
        // moment apart — a double-tapped button, or a retry sent while the first is
        // still in flight — both see no sale and both insert one. The loser hit a
        // primary key violation, which surfaced as a bare 409 the cashier could make no
        // sense of. One counter rings up one sale at a time regardless, so the lock
        // costs nothing real.
        sellerRepository.findByIdForUpdate(sellerId);

        // A till that lost its connection resends the same sale. Answering with the
        // original is the whole point of letting it choose the id.
        Purchase existing = purchaseRepository.findById(request.saleUuid()).orElse(null);
        if (existing != null) {
            return replay(existing, sellerId, request);
        }

        SellerProfile seller = sellerAccessGuard.requireActiveSellerForTrade(sellerId);

        PaymentMethod method = paymentMethodOf(request);
        if (method == PaymentMethod.KHQR) {
            requireCollectingAccount(seller);
        }

        Purchase sale = new Purchase();
        sale.setUuid(request.saleUuid());
        sale.setSellerProfile(seller);
        sale.setChannel(PurchaseChannel.POS);
        // A cash sale is done when it is rung up: the goods are already in the customer's
        // hands. A QR sale is not — the customer has yet to scan, and completing it now
        // would be recording money that may never arrive, so it waits for Bakong.
        sale.setStatus(method == PaymentMethod.KHQR
                ? PurchaseStatus.PENDING
                : PurchaseStatus.COMPLETED);
        if (method != PaymentMethod.KHQR) {
            sale.setCompletedAt(LocalDateTime.now());
        }
        sale.setBuyerId(null);
        sale.setPaymentMethod(paymentMethodOf(request));
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
            item.setUnitCost(Money.of(listing.getCostPrice()));
            sale.getItems().add(item);

            total = Money.add(total, Money.multiply(unitPrice, line.quantity()));
        }
        sale.setTotalPrice(total);
        applySoldAt(sale, request.soldAt());

        Purchase saved = purchaseRepository.save(sale);

        // Opened after the sale exists, because the payment names it: settling the
        // payment is what completes this sale, and expiring it is what gives the stock
        // back. The QR is drawn on the shop's own account, so the money goes straight to
        // them and this platform never holds it.
        Payment payment = method == PaymentMethod.KHQR
                ? paymentService.startForAccount(
                        PaymentPurpose.POS_SALE, saved.getUuid().toString(), sellerId,
                        saved.getTotalPrice(), PaymentCurrency.USD,
                        seller.getBakongAccountId(), collectingName(seller), seller.getCity())
                : null;

        return respond(saved, request, payment);
    }

    /** A counter takes cash unless the till says otherwise. */
    private static PaymentMethod paymentMethodOf(PosSaleRequest request) {
        return request.paymentMethod() == null ? PaymentMethod.CASH : request.paymentMethod();
    }

    /**
     * A QR has to be drawn on an account. Refused up front rather than after the stock
     * has moved, so a shop that has not supplied one is told plainly instead of ending
     * up with a sale nobody can pay.
     */
    private static void requireCollectingAccount(SellerProfile seller) {
        String account = seller.getBakongAccountId();
        if (account == null || account.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Add your Bakong account to your shop profile before taking KHQR "
                            + "payments at the counter.");
        }
    }

    /** What the payer reads when they scan; the business name unless one is set. */
    private static String collectingName(SellerProfile seller) {
        String name = seller.getBakongAccountName();
        return name == null || name.isBlank() ? seller.getBusinessName() : name;
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
        // A retried QR sale gets the same QR back: startForAccount hands back the live
        // payment for this sale rather than minting a second one.
        Payment payment = existing.getPaymentMethod() == PaymentMethod.KHQR
                ? paymentService.startForAccount(
                        PaymentPurpose.POS_SALE, existing.getUuid().toString(), sellerId,
                        existing.getTotalPrice(), PaymentCurrency.USD,
                        existing.getSellerProfile().getBakongAccountId(),
                        collectingName(existing.getSellerProfile()),
                        existing.getSellerProfile().getCity())
                : null;
        return respond(existing, request, payment);
    }

    /**
     * Works out the change, and refuses the combinations that cannot have happened.
     *
     * <p>Cash below the total is the obvious one. The subtler one is cash on a KHQR
     * sale: the customer transferred an exact amount, so there is no float to give
     * change from, and a receipt printing some would send them away short. Both are
     * refused rather than corrected, because either means the till is confused about
     * what just happened at the counter.
     */
    private PosSaleResponse respond(Purchase sale, PosSaleRequest request, Payment payment) {
        PaymentMethod method = paymentMethodOf(request);
        BigDecimal tendered = Money.of(request.amountTendered());

        if (tendered != null && method != PaymentMethod.CASH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cash tendered does not apply to a " + method + " sale.");
        }

        BigDecimal change = null;
        if (tendered != null) {
            if (Money.isLessThan(tendered, sale.getTotalPrice())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The amount tendered is less than the total of " + sale.getTotalPrice() + ".");
            }
            change = Money.subtract(tendered, sale.getTotalPrice());
        }

        // The stored method wins on a replay: what the sale was actually paid with was
        // settled the first time, and a retry that disagrees does not get to rewrite it.
        PaymentMethod recorded = sale.getPaymentMethod() == null ? method : sale.getPaymentMethod();
        return new PosSaleResponse(purchaseMapper.toResponse(sale), recorded, tendered, change,
                payment == null ? null : PaymentResponse.of(payment));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
