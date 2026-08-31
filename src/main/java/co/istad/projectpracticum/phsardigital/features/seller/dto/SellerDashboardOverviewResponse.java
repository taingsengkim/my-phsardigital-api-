package co.istad.projectpracticum.phsardigital.features.seller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the shop's home dashboard draws, in one answer.
 *
 * <p>It exists because the alternative was the client paging the order and listing
 * lists and adding them up itself, which is wrong the moment a shop has more orders
 * than one page holds — and silently wrong, since a truncated total still looks like a
 * total. Every figure below is computed by the database over the whole table.
 *
 * <p><strong>Two different meanings of revenue live here, deliberately kept apart.</strong>
 * {@link Revenue#lifetimeEarned} and {@link Revenue#inFlight} are settlement figures:
 * money collected, and money promised. The windowed figures — today, this week, this
 * month, and the chart — are <em>booked sales</em>: the value of orders placed in that
 * window that were not cancelled. Orders settle in cash on delivery, so a "today"
 * measured on delivery would show a shop nothing for the orders it actually took today.
 */
public record SellerDashboardOverviewResponse(
        Revenue revenue,
        Orders orders,
        Inventory inventory,
        List<DailySales> salesChart7Days,
        List<TopProduct> topProducts
) {

    /**
     * @param lifetimeEarned    delivered orders, all time — money actually collected
     * @param inFlight          placed and accepted but not yet delivered; expected, not
     *                          earned, and never added to {@link #lifetimeEarned}
     * @param todayRevenue      booked sales since midnight
     * @param thisWeekRevenue   booked sales since Monday
     * @param thisMonthRevenue  booked sales since the first of the month
     * @param percentageGrowth  the last 7 days against the 7 before them, as a
     *                          percentage. <strong>Null</strong> when the earlier week
     *                          had no sales: growth from nothing is not a number, and
     *                          reporting it as 100% or as infinity would both be lies.
     */
    public record Revenue(
            BigDecimal lifetimeEarned,
            BigDecimal inFlight,
            BigDecimal todayRevenue,
            BigDecimal thisWeekRevenue,
            BigDecimal thisMonthRevenue,
            BigDecimal percentageGrowth
    ) {
    }

    /**
     * @param pending    waiting for the shop to accept — the number that means work today
     * @param todayCount orders placed since midnight, however they have since turned out
     */
    public record Orders(
            long total,
            long pending,
            long confirmed,
            long completed,
            long cancelled,
            long todayCount
    ) {
    }

    /**
     * @param soldOutProducts     listings whose status says sold out
     * @param lowStockProducts    on sale, still in stock, but at or under the warning
     *                            threshold. Counts only listings a buyer could actually
     *                            order, and excludes anything already at zero, so this
     *                            and {@link #soldOutProducts} never count the same row.
     * @param totalInventoryUnits units held across listings that are on sale
     */
    public record Inventory(
            long totalProducts,
            long activeProducts,
            long draftProducts,
            long soldOutProducts,
            long lowStockProducts,
            long totalInventoryUnits
    ) {
    }

    /**
     * @param dayLabel short weekday name in English, e.g. {@code Mon}. Sent so every
     *                 client labels the axis identically; a client that localises its
     *                 own dates should render {@link #date} instead.
     */
    public record DailySales(
            LocalDate date,
            String dayLabel,
            long ordersCount,
            BigDecimal revenue
    ) {
    }

    /**
     * @param unitsSold    units moved across accepted orders
     * @param totalRevenue what those units were charged at, frozen at the price each
     *                     order paid rather than the listing's price now
     * @param title        the listing's current title, unlike the frozen one an order
     *                     line keeps — this is a live catalogue view, not a receipt
     */
    public record TopProduct(
            UUID listingUuid,
            String title,
            String slug,
            String thumbnailUrl,
            long unitsSold,
            BigDecimal totalRevenue
    ) {
    }
}
