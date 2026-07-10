package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import jakarta.validation.constraints.NotBlank;

public record SellerApplicationRequest(
        @NotBlank(message = "Business name is required")
        String businessName,
        String businessType,
        String description,
        String city,
        String province
) {
}