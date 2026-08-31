package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A shop's order book at a glance: the tiles above the order list, and the counts on
 * its filter tabs.
 *
 * <p>Counts and money in one answer because they are read together and drawn together.
 * A client that had to page four lists to count them would make four requests to render
 * one row of tiles, and still could not total the takings.
 *
 * @param pending          orders waiting for the seller to accept — the number that
 *                         means work to do today
 * @param earnedRevenue    takings from delivered orders. Money actually collected,
 *                         since orders settle in cash on delivery.
 * @param inFlightRevenue  value of accepted orders not yet delivered — expected, not
 *                         earned. Kept apart from {@link #earnedRevenue} deliberately:
 *                         one is money in the till and the other is a promise, and a
 *                         dashboard that adds them together overstates the shop.
 */
public record SellerOrderSummaryResponse(
        long pending,
        long confirmed,
        long completed,
        long cancelled,
        long total,
        BigDecimal earnedRevenue,
        BigDecimal inFlightRevenue
) {

    /**
     * @param counts  order count per state, missing entries meaning none
     * @param revenue takings per state, on the same terms
     */
    public static SellerOrderSummaryResponse of(Map<PurchaseStatus, Long> counts,
                                                Map<PurchaseStatus, BigDecimal> revenue) {
        long pending = count(counts, PurchaseStatus.PENDING);
        long confirmed = count(counts, PurchaseStatus.CONFIRMED);
        long completed = count(counts, PurchaseStatus.COMPLETED);
        long cancelled = count(counts, PurchaseStatus.CANCELLED);

        return new SellerOrderSummaryResponse(
                pending,
                confirmed,
                completed,
                cancelled,
                pending + confirmed + completed + cancelled,
                money(revenue, PurchaseStatus.COMPLETED),
                money(revenue, PurchaseStatus.CONFIRMED));
    }

    private static long count(Map<PurchaseStatus, Long> counts, PurchaseStatus status) {
        return counts.getOrDefault(status, 0L);
    }

    /** Zero rather than null, so a shop with no orders still renders a tile. */
    private static BigDecimal money(Map<PurchaseStatus, BigDecimal> revenue, PurchaseStatus status) {
        return Money.of(revenue.getOrDefault(status, Money.ZERO));
    }
}
