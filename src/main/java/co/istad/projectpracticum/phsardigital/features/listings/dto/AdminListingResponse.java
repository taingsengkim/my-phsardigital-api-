package co.istad.projectpracticum.phsardigital.features.listings.dto;

import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A listing as the moderation table shows it.
 *
 * <p>Separate from {@link ListingResponse} on purpose: that one answers the public
 * catalogue, and putting {@link #moderationReason} on it would tell every buyer why a
 * product was taken down — and tell the seller through a route they can read freely.
 *
 * @param statusBeforeSuspension what a restore would put the listing back to, or null
 *                               when it was never taken down
 */
public record AdminListingResponse(
        UUID uuid,
        String title,
        String slug,
        String sellerId,
        String businessName,
        String categoryName,
        BigDecimal fullPrice,
        BigDecimal discountPrice,
        Integer stockQty,
        Integer sold,
        ListingStatus status,
        ListingStatus statusBeforeSuspension,
        String thumbnailUri,
        String moderatedBy,
        LocalDateTime moderatedAt,
        String moderationReason,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {
}
