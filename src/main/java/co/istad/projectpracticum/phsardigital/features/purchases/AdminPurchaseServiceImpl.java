package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseBuyerResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseDetailResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseItemSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseRowResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseSellerResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseStatusTotalsResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseActivityResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPurchaseServiceImpl implements AdminPurchaseService {

    /** Orders nobody at the shop has accepted yet — the queue an administrator chases. */
    private static final PurchaseStatus NEEDS_ACTION = PurchaseStatus.PENDING;

    /** A buyer's lifetime figures count only orders that actually completed. */
    private static final PurchaseStatus SETTLED = PurchaseStatus.COMPLETED;

    /**
     * How many orders one export may carry. An admin filtering to "everything" would
     * otherwise stream the entire order history through one request, and the first sign
     * of trouble would be the server running out of heap.
     */
    private static final int EXPORT_LIMIT = 20_000;

    /** Rows fetched per round trip while building an export. */
    private static final int EXPORT_BATCH = 500;

    private final PurchaseRepository purchaseRepository;
    private final UserProfileRepository userProfileRepository;
    private final PurchaseMapper purchaseMapper;
    private final FileUploadService fileUploadService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPurchaseRowResponse> list(AdminPurchaseFilter filter, int pageNumber,
                                               int pageSize, String sortBy, String sortOrder) {
        Pageable pageable = PageRequest.of(
                pageNumber, pageSize, AdminPurchaseSort.parse(sortBy, sortOrder));
        Page<Purchase> page = purchaseRepository.findAll(
                AdminPurchaseSpecifications.matching(filter), pageable);

        return new org.springframework.data.domain.PageImpl<>(
                toRows(page.getContent()), pageable, page.getTotalElements());
    }

    /**
     * A page of orders as table rows.
     *
     * <p>Three queries for the whole page however long it is — the orders, their lines,
     * and the buyers — rather than the two-per-row the obvious loop would cost.
     * {@code PurchaseMapper} looks a buyer up per order, which is right for one order and
     * wrong for a table of them.
     */
    private List<AdminPurchaseRowResponse> toRows(List<Purchase> purchases) {
        if (purchases.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<PurchaseItem>> itemsByOrder = itemsFor(purchases);
        Map<String, UserProfile> buyers = buyersFor(purchases);

        List<AdminPurchaseRowResponse> rows = new ArrayList<>();
        for (Purchase purchase : purchases) {
            UserProfile buyer = purchase.getBuyerId() == null
                    ? null
                    : buyers.get(purchase.getBuyerId());
            rows.add(new AdminPurchaseRowResponse(
                    purchase.getUuid(),
                    purchase.getCreatedAt(),
                    purchase.getStatus(),
                    purchase.getChannel(),
                    purchase.getPaymentMethod(),
                    Money.of(purchase.getTotalPrice()),
                    toBuyerSummary(purchase, buyer),
                    toSeller(purchase.getSellerProfile()),
                    toItemSummary(itemsByOrder.getOrDefault(purchase.getUuid(), List.of())),
                    purchase.getConfirmedAt(),
                    purchase.getCompletedAt(),
                    purchase.getCancelledAt()));
        }
        return rows;
    }

    private Map<UUID, List<PurchaseItem>> itemsFor(List<Purchase> purchases) {
        List<UUID> uuids = purchases.stream().map(Purchase::getUuid).toList();
        Map<UUID, List<PurchaseItem>> byOrder = new HashMap<>();
        for (PurchaseItem item : purchaseRepository.findItemsForPurchases(uuids)) {
            byOrder.computeIfAbsent(item.getPurchase().getUuid(), key -> new ArrayList<>())
                    .add(item);
        }
        return byOrder;
    }

    private Map<String, UserProfile> buyersFor(List<Purchase> purchases) {
        // Counter sales have no buyer, and a distinct set keeps a page where one person
        // ordered ten times from asking for them ten times.
        Set<String> buyerIds = new HashSet<>();
        purchases.forEach(purchase -> {
            if (purchase.getBuyerId() != null) {
                buyerIds.add(purchase.getBuyerId());
            }
        });
        if (buyerIds.isEmpty()) {
            return Map.of();
        }
        Map<String, UserProfile> buyers = new HashMap<>();
        userProfileRepository.findAllById(buyerIds)
                .forEach(profile -> buyers.put(profile.getId(), profile));
        return buyers;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPurchaseSummaryResponse summarise(LocalDateTime from, LocalDateTime to) {
        Window current = Window.of(purchaseRepository.summariseOrdersPlacedBetween(from, to));

        // The window immediately before this one, of the same length, so "up 14%" means
        // something a reader can check rather than a comparison against a fixed month.
        Duration length = Duration.between(from, to);
        Window previous = Window.of(
                purchaseRepository.summariseOrdersPlacedBetween(from.minus(length), from));

        Map<PurchaseStatus, AdminPurchaseStatusTotalsResponse> byStatus =
                new EnumMap<>(PurchaseStatus.class);
        for (PurchaseStatus status : PurchaseStatus.values()) {
            // Every status present, including the empty ones: a tab badge that vanishes
            // when a queue empties reads as a broken tab, not as zero.
            byStatus.put(status, new AdminPurchaseStatusTotalsResponse(
                    current.count(status), Money.of(current.value(status))));
        }

        return new AdminPurchaseSummaryResponse(
                from,
                to,
                Money.of(current.merchandiseValue()),
                current.totalOrders(),
                averageOrderValue(current),
                Money.of(previous.merchandiseValue()),
                growthPercent(current.merchandiseValue(), previous.merchandiseValue()),
                byStatus,
                current.count(NEEDS_ACTION));
    }

    /** Over orders that were not cancelled — a cancelled order is not a smaller sale. */
    private static BigDecimal averageOrderValue(Window window) {
        long orders = window.sellingOrders();
        if (orders == 0) {
            return Money.ZERO;
        }
        return window.merchandiseValue()
                .divide(BigDecimal.valueOf(orders), Money.SCALE, Money.ROUNDING);
    }

    /**
     * @return null when the earlier window sold nothing. Growth from zero is not a
     *         percentage: reporting it as 0% hides a launch and as ∞% is not a number a
     *         card can render.
     */
    private static Double growthPercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPurchaseDetailResponse get(UUID uuid) {
        Purchase purchase = require(uuid);
        UserProfile buyer = purchase.getBuyerId() == null
                ? null
                : userProfileRepository.findById(purchase.getBuyerId()).orElse(null);

        return new AdminPurchaseDetailResponse(
                // The same payload the order's own buyer and seller are served, so the
                // three views cannot drift apart.
                purchaseMapper.toResponse(purchase),
                toBuyerDetail(purchase, buyer),
                toSeller(purchase.getSellerProfile()),
                toActivities(purchase));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseActivityResponse> activities(UUID uuid) {
        return toActivities(require(uuid));
    }

    /**
     * The order's lifecycle, read off the four moments it records.
     *
     * <p>Nothing is inferred beyond what those prove. A cancellation names no actor
     * because either side may cancel and the column does not say which — see
     * {@link PurchaseActorType#UNKNOWN}.
     */
    private List<PurchaseActivityResponse> toActivities(Purchase purchase) {
        List<PurchaseActivityResponse> timeline = new ArrayList<>();
        String shop = purchase.getSellerProfile().getBusinessName();
        boolean counterSale = purchase.getBuyerId() == null;

        if (purchase.getCreatedAt() != null) {
            timeline.add(counterSale
                    ? new PurchaseActivityResponse(purchase.getCreatedAt(),
                    PurchaseActivityAction.ORDER_PLACED, PurchaseActorType.SELLER, shop,
                    "Rung up at the counter.")
                    : new PurchaseActivityResponse(purchase.getCreatedAt(),
                    PurchaseActivityAction.ORDER_PLACED, PurchaseActorType.BUYER,
                    purchase.getRecipientName(), "Order placed, waiting for the shop."));
        }
        if (purchase.getConfirmedAt() != null) {
            timeline.add(new PurchaseActivityResponse(purchase.getConfirmedAt(),
                    PurchaseActivityAction.ORDER_CONFIRMED, PurchaseActorType.SELLER, shop,
                    "Shop accepted the order and reserved stock."));
        }
        if (purchase.getCompletedAt() != null) {
            timeline.add(new PurchaseActivityResponse(purchase.getCompletedAt(),
                    PurchaseActivityAction.ORDER_COMPLETED, PurchaseActorType.SELLER, shop,
                    "Delivered and settled."));
        }
        if (purchase.getCancelledAt() != null) {
            timeline.add(new PurchaseActivityResponse(purchase.getCancelledAt(),
                    PurchaseActivityAction.ORDER_CANCELLED, PurchaseActorType.UNKNOWN, null,
                    "Order cancelled. Nothing recorded which side cancelled it."));
        }

        timeline.sort(Comparator.comparing(PurchaseActivityResponse::timestamp));
        return timeline;
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(AdminPurchaseFilter filter, String sortBy, String sortOrder) {
        var sort = AdminPurchaseSort.parse(sortBy, sortOrder);
        var specification = AdminPurchaseSpecifications.matching(filter);

        StringBuilder csv = new StringBuilder(AdminPurchaseCsvWriter.HEADER);
        int exported = 0;
        for (int pageNumber = 0; exported < EXPORT_LIMIT; pageNumber++) {
            Page<Purchase> page = purchaseRepository.findAll(
                    specification, PageRequest.of(pageNumber, EXPORT_BATCH, sort));
            if (page.isEmpty()) {
                break;
            }
            for (AdminPurchaseRowResponse row : toRows(page.getContent())) {
                csv.append(AdminPurchaseCsvWriter.toRow(row));
                if (++exported >= EXPORT_LIMIT) {
                    break;
                }
            }
            if (!page.hasNext()) {
                break;
            }
        }
        return csv.toString();
    }

    private Purchase require(UUID uuid) {
        return purchaseRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found."));
    }

    /** Identity only. The list route computes no per-buyer aggregates — see the DTO. */
    private AdminPurchaseBuyerResponse toBuyerSummary(Purchase purchase, UserProfile buyer) {
        if (purchase.getBuyerId() == null) {
            return null;
        }
        return AdminPurchaseBuyerResponse.summary(
                purchase.getBuyerId(),
                displayName(purchase, buyer),
                purchase.getRecipientPhone() != null
                        ? purchase.getRecipientPhone()
                        : (buyer == null ? null : buyer.getPhone()),
                buyer == null ? null : buyer.getEmail(),
                avatarUrl(buyer));
    }

    /** As above, plus what this buyer has spent across the marketplace. */
    private AdminPurchaseBuyerResponse toBuyerDetail(Purchase purchase, UserProfile buyer) {
        AdminPurchaseBuyerResponse summary = toBuyerSummary(purchase, buyer);
        if (summary == null) {
            return null;
        }

        long orders = 0;
        BigDecimal spend = Money.ZERO;
        List<Object[]> stats = purchaseRepository.orderStatsForBuyers(
                SETTLED.name(), List.of(purchase.getBuyerId()));
        if (!stats.isEmpty()) {
            Object[] row = stats.getFirst();
            orders = ((Number) row[1]).longValue();
            spend = Money.of((BigDecimal) row[2]);
        }

        return new AdminPurchaseBuyerResponse(summary.id(), summary.name(), summary.phone(),
                summary.email(), summary.avatarUrl(), orders, spend);
    }

    /**
     * The recipient wins over the account holder, matching {@code PurchaseMapper}: people
     * order to a parent's or a colleague's address, and it is the recipient the courier
     * asks for.
     */
    private static String displayName(Purchase purchase, UserProfile buyer) {
        if (purchase.getRecipientName() != null) {
            return purchase.getRecipientName();
        }
        return buyer == null ? null : buyer.getFullName();
    }

    /** An uploaded avatar wins over the one the identity provider supplied. */
    private String avatarUrl(UserProfile buyer) {
        if (buyer == null) {
            return null;
        }
        FileUpload avatar = buyer.getAvatarFile();
        return avatar != null ? fileUploadService.getPreviewUrl(avatar) : buyer.getExternalAvatarUrl();
    }

    private AdminPurchaseSellerResponse toSeller(SellerProfile shop) {
        if (shop == null) {
            return null;
        }
        return new AdminPurchaseSellerResponse(
                shop.getSellerId(),
                shop.getBusinessName(),
                shop.getPhoneNumber(),
                shop.getLogoFile() == null ? null : fileUploadService.getPreviewUrl(shop.getLogoFile()),
                shop.getIsActive());
    }

    private AdminPurchaseItemSummaryResponse toItemSummary(List<PurchaseItem> items) {
        if (items.isEmpty()) {
            // An order with no lines is not something checkout can produce, but a table
            // must still render the row rather than fail the whole page over it.
            return new AdminPurchaseItemSummaryResponse(0, 0, null, null, false);
        }
        int units = items.stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum();
        Listing first = items.getFirst().getListing();
        return new AdminPurchaseItemSummaryResponse(
                items.size(),
                units,
                first.getTitle(),
                first.getThumbnailFile() == null
                        ? null
                        : fileUploadService.getPreviewUrl(first.getThumbnailFile()),
                items.size() > 1);
    }

    /**
     * One window's per-status counts and values, as the aggregate returns them.
     *
     * <p>States with no orders are absent from the query rather than returned as zero
     * rows, so every read goes through this instead of indexing the list.
     */
    private record Window(Map<PurchaseStatus, AdminPurchaseStatusTotalsResponse> rows) {

        static Window of(List<Object[]> aggregate) {
            Map<PurchaseStatus, AdminPurchaseStatusTotalsResponse> rows =
                    new LinkedHashMap<>();
            for (Object[] row : aggregate) {
                rows.put(PurchaseStatus.valueOf((String) row[0]),
                        new AdminPurchaseStatusTotalsResponse(
                                ((Number) row[1]).longValue(), (BigDecimal) row[2]));
            }
            return new Window(rows);
        }

        long count(PurchaseStatus status) {
            AdminPurchaseStatusTotalsResponse row = rows.get(status);
            return row == null ? 0L : row.orderCount();
        }

        BigDecimal value(PurchaseStatus status) {
            AdminPurchaseStatusTotalsResponse row = rows.get(status);
            return row == null ? Money.ZERO : row.value();
        }

        /** Every order placed in the window, cancelled ones included. */
        long totalOrders() {
            return rows.values().stream()
                    .mapToLong(AdminPurchaseStatusTotalsResponse::orderCount)
                    .sum();
        }

        /** Orders that still represent a sale. */
        long sellingOrders() {
            return totalOrders() - count(PurchaseStatus.CANCELLED);
        }

        /** What buyers paid shops, excluding orders that came to nothing. */
        BigDecimal merchandiseValue() {
            BigDecimal total = Money.ZERO;
            for (Map.Entry<PurchaseStatus, AdminPurchaseStatusTotalsResponse> entry : rows.entrySet()) {
                if (entry.getKey() != PurchaseStatus.CANCELLED) {
                    total = Money.add(total, entry.getValue().value());
                }
            }
            return total;
        }
    }
}
