package co.istad.projectpracticum.phsardigital.features.seller.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param averageRating    the shop's star rating to one decimal place, or null when it
 *                         has no reviews yet
 * @param reviewCount      how many reviews that average is over
 * @param suspensionReason why an admin took the shop down, filled in only on the
 *                         seller's own routes. Null means "not disclosed here", not
 *                         "not suspended" — {@code isActive} answers that.
 * @param suspendedAt      when that happened, disclosed on the same terms
 */
public record SellerProfileResponse(
        String id,
        String businessName,
        String businessType,
        String description,
        String logoObjectName,
        String logoUri,
        String address,
        String city,
        String province,
        BigDecimal latitude,
        BigDecimal longitude,
        String googleMapUrl,
        Boolean isActive,
        String phoneNumber,
        String biography,
        List<String> socialLink,
        Double averageRating,
        Long reviewCount,
        String suspensionReason,
        LocalDateTime suspendedAt
) {
}
