package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * The KPI row above the admin orders table, and the badge counts on its status tabs.
 *
 * <p>Computed in the database over the whole window rather than from the page on screen.
 * A dashboard that sums the twenty rows it happens to be showing reports the wrong
 * number confidently, which is worse than reporting none.
 *
 * @param grossMerchandiseValue what buyers paid shops on orders placed in the window,
 *                              excluding cancelled ones. This is merchandise value, not
 *                              platform earnings: the marketplace takes no cut of an
 *                              order and never holds the money. Platform revenue is
 *                              seller subscriptions, reported elsewhere.
 * @param growthPercent         GMV against the preceding window of equal length, as a
 *                              percentage. Null when the earlier window had no sales at
 *                              all — growth from zero is not a percentage, and rendering
 *                              it as 0% or ∞% both mislead.
 * @param byStatus              every status is present, including those with no orders,
 *                              so a tab badge reads 0 rather than disappearing
 * @param needingAction         orders still PENDING: nobody at the shop has accepted
 *                              them yet, which is the queue an admin chases
 */
public record AdminPurchaseSummaryResponse(
        LocalDateTime from,
        LocalDateTime to,
        BigDecimal grossMerchandiseValue,
        long totalOrders,
        BigDecimal averageOrderValue,
        BigDecimal previousGrossMerchandiseValue,
        Double growthPercent,
        Map<PurchaseStatus, AdminPurchaseStatusTotalsResponse> byStatus,
        long needingAction
) {
}
