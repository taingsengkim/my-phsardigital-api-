package co.istad.projectpracticum.phsardigital.features.seller.dto;

import java.math.BigDecimal;

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
        Boolean isActive
) {
}
