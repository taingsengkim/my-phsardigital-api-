package co.istad.projectpracticum.phsardigital.features.admin.dto;

import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * The numbers behind the admin dashboard's header cards.
 *
 * <p>There is deliberately no {@code totalRevenue}. Purchases settle in cash between
 * buyer and seller, so {@link #completedSalesValue} is merchandise value, not the
 * platform's money; and subscriptions take no payment and keep no history, so they
 * cannot be summed at all. {@link #activeSubscriptionsByPlan} stands in until there
 * is a payment ledger.
 *
 * @param totalSellers        every shop, including suspended ones
 * @param completedSalesValue merchandise value over {@code COMPLETED} orders only
 */
public record AdminDashboardSummaryResponse(
        long totalUsers,
        long totalSellers,
        long activeSellers,
        long totalListings,
        long activeListings,
        long pendingApplications,
        long completedPurchases,
        BigDecimal completedSalesValue,
        long activeSubscriptions,
        Map<SubscriptionPlan, Long> activeSubscriptionsByPlan,
        LocalDateTime generatedAt
) {
}
