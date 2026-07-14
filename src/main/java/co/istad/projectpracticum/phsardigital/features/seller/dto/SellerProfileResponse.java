package co.istad.projectpracticum.phsardigital.features.seller.dto;

public record SellerProfileResponse(
        String id,
        String businessName,
        String businessType,
        String description,
        String address,
        String city,
        String province,
        Boolean isActive
) {
}
