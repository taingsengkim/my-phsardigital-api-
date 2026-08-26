package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;

import java.math.BigDecimal;

/**
 * @param listingLimit how many listings the plan allows, or null when unlimited —
 *                     null rather than the {@code -1} sentinel, so clients render
 *                     "Unlimited" without knowing the sentinel
 * @param active       whether the plan is offered to new subscribers. Always true on
 *                     the public pricing page, which lists only active plans; the
 *                     admin catalogue shows retired ones too
 */
public record SubscriptionPlanResponse(
        String code,
        String displayName,
        BigDecimal priceUsd,
        int durationDays,
        Integer listingLimit,
        boolean active,
        int sortOrder
) {

    public static SubscriptionPlanResponse of(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getCode(),
                plan.getDisplayName(),
                plan.getPriceUsd(),
                plan.getDurationDays(),
                plan.listingLimitOrNull(),
                plan.isActive(),
                plan.getSortOrder());
    }
}
