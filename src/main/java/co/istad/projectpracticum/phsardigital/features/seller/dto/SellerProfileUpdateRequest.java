package co.istad.projectpracticum.phsardigital.features.seller.dto;

public record SellerProfileUpdateRequest(
        String businessName,
        String businessType,
        String description,
        String address,
        String city,
        String province
) {
}
