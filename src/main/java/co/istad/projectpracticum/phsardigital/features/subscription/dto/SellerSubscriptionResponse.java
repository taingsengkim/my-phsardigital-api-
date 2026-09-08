package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionStatus;

import java.time.LocalDateTime;

/**
 * @param seller        who holds this subscription. Filled on the admin listing, where
 *                      a page of bare subject ids is unreadable; null on the seller's
 *                      own {@code /subscriptions/me}, where the caller is the shop and
 *                      the block would only repeat what they already know. Null there
 *                      means "not looked up on this route", not "no such shop" — and it
 *                      is also null for a subscription granted against an id that has
 *                      no shop profile behind it.
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
        SubscriberResponse seller,
        String planCode,
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
