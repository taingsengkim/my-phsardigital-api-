package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;

import java.math.BigDecimal;

/**
 * @param listingLimit    how many listings the plan allows, or null when unlimited —
 *                        null rather than the {@code -1} sentinel, so clients render
 *                        "Unlimited" without knowing the sentinel
 */
public record SubscriptionPlanResponse(
        SubscriptionPlan plan,
        String displayName,
        BigDecimal priceUsd,
        int durationDays,
        Integer listingLimit
) {
}
