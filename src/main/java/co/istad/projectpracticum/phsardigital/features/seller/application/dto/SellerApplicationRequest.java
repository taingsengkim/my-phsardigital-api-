package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sizes mirror the column widths on {@code seller_applications}, so over-long
 * input is rejected as a 400 instead of failing at insert time as a 500.
 */
public record SellerApplicationRequest(
        @NotBlank(message = "Business name is required")
        @Size(max = 255, message = "Business name must not exceed 255 characters")
        String businessName,

        @Size(max = 100, message = "Business type must not exceed 100 characters")
        String businessType,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "Province must not exceed 100 characters")
        String province
) {
}
