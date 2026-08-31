package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerDashboardOverviewResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerDashboardOverviewResponse.DailySales;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerDashboardOverviewResponse.Inventory;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerDashboardOverviewResponse.Orders;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerDashboardOverviewResponse.Revenue;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerDashboardOverviewResponse.TopProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerDashboardServiceImpl implements SellerDashboardService {

    /** Days on the chart, including today. */
    private static final int CHART_DAYS = 7;

    /** How many best sellers the dashboard shows. */
    private static final int TOP_PRODUCTS = 5;

    /**
     * Orders a shop has agreed to. A PENDING order is a request nobody has accepted and
     * a CANCELLED one came to nothing, so neither belongs in a bestseller ranking.
     */
    private static final Collection<PurchaseStatus> ACCEPTED_ORDERS =
            EnumSet.of(PurchaseStatus.CONFIRMED, PurchaseStatus.COMPLETED);

    private final PurchaseRepository purchaseRepository;
    private final ListingRepository listingRepository;
    private final FileUploadService fileUploadService;

    /**
     * At or below this many units, a listing on sale is reported as running low.
     * Configurable because what counts as low depends on what the shop sells.
     */
    @Value("${app.seller.low-stock-threshold:5}")
    private int lowStockThreshold;

    @Override
    @Transactional(readOnly = true)
    public SellerDashboardOverviewResponse getMyOverview() {
        String sellerId = AuthUtils.extractUserId();
        LocalDateTime now = LocalDateTime.now();

        return new SellerDashboardOverviewResponse(
                revenue(sellerId, now),
                orders(sellerId, now),
                inventory(sellerId),
                salesChart(sellerId, now.toLocalDate()),
                topProducts(sellerId));
    }

    // ---------- revenue ----------

    private Revenue revenue(String sellerId, LocalDateTime now) {
        Map<PurchaseStatus, BigDecimal> byStatus = new EnumMap<>(PurchaseStatus.class);
        for (Object[] row : purchaseRepository.summariseOrdersForSeller(sellerId)) {
            byStatus.put(PurchaseStatus.valueOf((String) row[0]), (BigDecimal) row[2]);
        }

        BigDecimal inFlight = Money.add(
                byStatus.getOrDefault(PurchaseStatus.PENDING, Money.ZERO),
                byStatus.getOrDefault(PurchaseStatus.CONFIRMED, Money.ZERO));

        LocalDate today = now.toLocalDate();
        LocalDateTime midnight = today.atStartOfDay();
        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        // Both windows end where the other begins, so an order is counted in exactly one
        // of them and the comparison is like for like.
        LocalDateTime sevenDaysAgo = midnight.minusDays(CHART_DAYS - 1L);
        LocalDateTime fourteenDaysAgo = sevenDaysAgo.minusDays(CHART_DAYS);
        BigDecimal thisPeriod = sales(sellerId, sevenDaysAgo, now);
        BigDecimal previousPeriod = sales(sellerId, fourteenDaysAgo, sevenDaysAgo);

        return new Revenue(
                Money.of(byStatus.getOrDefault(PurchaseStatus.COMPLETED, Money.ZERO)),
                inFlight,
                sales(sellerId, midnight, now),
                sales(sellerId, weekStart, now),
                sales(sellerId, monthStart, now),
                growth(previousPeriod, thisPeriod));
    }

    private BigDecimal sales(String sellerId, LocalDateTime from, LocalDateTime to) {
        return Money.of(purchaseRepository.sumSalesPlacedBetween(sellerId, from, to));
    }

    /**
     * @return the change as a percentage, or null when the earlier period had no sales.
     *         Every alternative to null there is a fiction: growth from zero is not a
     *         percentage, and a dashboard claiming 100% because a shop sold its first
     *         item is worse than one that shows nothing.
     */
    private static BigDecimal growth(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    // ---------- orders ----------

    private Orders orders(String sellerId, LocalDateTime now) {
        Map<PurchaseStatus, Long> counts = new EnumMap<>(PurchaseStatus.class);
        for (Object[] row : purchaseRepository.summariseOrdersForSeller(sellerId)) {
            counts.put(PurchaseStatus.valueOf((String) row[0]), ((Number) row[1]).longValue());
        }

        long pending = counts.getOrDefault(PurchaseStatus.PENDING, 0L);
        long confirmed = counts.getOrDefault(PurchaseStatus.CONFIRMED, 0L);
        long completed = counts.getOrDefault(PurchaseStatus.COMPLETED, 0L);
        long cancelled = counts.getOrDefault(PurchaseStatus.CANCELLED, 0L);

        long today = purchaseRepository
                .countBySellerProfile_SellerIdAndCreatedAtGreaterThanEqual(
                        sellerId, now.toLocalDate().atStartOfDay());

        return new Orders(pending + confirmed + completed + cancelled,
                pending, confirmed, completed, cancelled, today);
    }

    // ---------- inventory ----------

    private Inventory inventory(String sellerId) {
        Map<ListingStatus, Long> counts = new EnumMap<>(ListingStatus.class);
        Map<ListingStatus, Long> units = new EnumMap<>(ListingStatus.class);
        for (Object[] row : listingRepository.summariseInventoryForSeller(sellerId)) {
            ListingStatus status = (ListingStatus) row[0];
            counts.put(status, ((Number) row[1]).longValue());
            units.put(status, ((Number) row[2]).longValue());
        }

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long lowStock = listingRepository.countLowStockForSeller(
                sellerId, ListingStatus.ACTIVE, lowStockThreshold);

        return new Inventory(
                total,
                counts.getOrDefault(ListingStatus.ACTIVE, 0L),
                counts.getOrDefault(ListingStatus.DRAFT, 0L),
                counts.getOrDefault(ListingStatus.SOLD_OUT, 0L),
                lowStock,
                // Stock a buyer could actually order. Counting drafts and archived rows
                // would inflate it with things nobody can buy.
                units.getOrDefault(ListingStatus.ACTIVE, 0L));
    }

    // ---------- chart ----------

    /**
     * The last seven days, every one of them present.
     *
     * <p>The query returns only days the shop sold something, because a database cannot
     * invent dates it has no rows for. A chart with gaps in the axis is worse than one
     * with zeroes, so the missing days are filled in here.
     */
    private List<DailySales> salesChart(String sellerId, LocalDate today) {
        LocalDate from = today.minusDays(CHART_DAYS - 1L);

        Map<LocalDate, Object[]> byDay = new HashMap<>();
        for (Object[] row : purchaseRepository.dailySalesForSeller(sellerId, from.atStartOfDay())) {
            byDay.put(toLocalDate(row[0]), row);
        }

        List<DailySales> chart = new ArrayList<>(CHART_DAYS);
        for (int offset = 0; offset < CHART_DAYS; offset++) {
            LocalDate day = from.plusDays(offset);
            Object[] row = byDay.get(day);
            chart.add(new DailySales(
                    day,
                    day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    row == null ? 0L : ((Number) row[1]).longValue(),
                    row == null ? Money.ZERO : Money.of((BigDecimal) row[2])));
        }
        return chart;
    }

    /** Drivers disagree about what a SQL {@code date} maps to, so both shapes are handled. */
    private static LocalDate toLocalDate(Object value) {
        return switch (value) {
            case LocalDate date -> date;
            case Date date -> date.toLocalDate();
            default -> LocalDate.parse(value.toString());
        };
    }

    // ---------- best sellers ----------

    private List<TopProduct> topProducts(String sellerId) {
        List<Object[]> ranked = purchaseRepository.rankSellerProductsByUnits(
                sellerId, ACCEPTED_ORDERS, PageRequest.of(0, TOP_PRODUCTS));
        if (ranked.isEmpty()) {
            return List.of();
        }

        // One catalogue read for the whole list rather than one per row.
        List<UUID> uuids = ranked.stream().map(row -> (UUID) row[0]).toList();
        Map<UUID, Listing> listings = new HashMap<>();
        listingRepository.findAllById(uuids)
                .forEach(listing -> listings.put(listing.getUuid(), listing));

        List<TopProduct> products = new ArrayList<>(ranked.size());
        for (Object[] row : ranked) {
            Listing listing = listings.get((UUID) row[0]);
            if (listing == null) {
                // Ranked from order lines, so a listing deleted outright would otherwise
                // put a hole in the list. Nothing deletes listings today; this is here so
                // that if something ever does, the dashboard degrades instead of failing.
                continue;
            }
            products.add(new TopProduct(
                    listing.getUuid(),
                    listing.getTitle(),
                    listing.getSlug(),
                    listing.getThumbnailFile() == null
                            ? null
                            : fileUploadService.getPreviewUrl(listing.getThumbnailFile()),
                    ((Number) row[1]).longValue(),
                    Money.of((BigDecimal) row[2])));
        }
        return products;
    }
}
