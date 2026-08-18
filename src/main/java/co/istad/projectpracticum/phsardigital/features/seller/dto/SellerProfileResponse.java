package co.istad.projectpracticum.phsardigital.features.seller.dto;

import java.math.BigDecimal;

/**
 * @param averageRating the shop's star rating to one decimal place, or null when it
 *                      has no reviews yet
 * @param reviewCount   how many reviews that average is over
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
        Double averageRating,
        Long reviewCount
) {
}
