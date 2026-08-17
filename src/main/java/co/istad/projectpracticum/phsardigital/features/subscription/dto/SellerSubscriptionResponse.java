package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionStatus;

import java.time.LocalDateTime;

/**
 * @param listingsUsed  listings currently counting against the plan
 * @param listingLimit  the plan's cap, or null when unlimited
 * @param canPostListing whether one more listing would be accepted right now — the
 *                       same question {@code POST /api/v1/listings} asks, answered
 *                       up front so the UI can disable the button instead of
 *                       discovering the refusal after the seller fills the form
 * @param canChat        whether the seller may send messages right now
 */
public record SellerSubscriptionResponse(
        String sellerId,
        SubscriptionPlan plan,
        String planDisplayName,
        SubscriptionStatus status,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        long listingsUsed,
        Integer listingLimit,
        boolean canPostListing,
        boolean canChat
) {
}
