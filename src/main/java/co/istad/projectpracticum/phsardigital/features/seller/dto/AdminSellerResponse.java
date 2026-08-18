package co.istad.projectpracticum.phsardigital.features.seller.dto;

import java.time.LocalDateTime;

/**
 * A shop as an admin sees it.
 *
 * <p>Separate from {@link SellerProfileResponse} because that one answers the public
 * shop page — putting the suspension reason on it would show every buyer why a shop
 * was taken down.
 */
public record AdminSellerResponse(
        String sellerId,
        String businessName,
        Boolean isActive,
        String suspendedBy,
        LocalDateTime suspendedAt,
        String suspensionReason,
        LocalDateTime createdAt
) {
}
