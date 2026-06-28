package co.istad.sengkim.phsardigital.features.listings.listing_images.dto;

import jakarta.validation.constraints.NotBlank;

public record ListingImageRequest(
        @NotBlank(message = "Object name must not be blank")
        String objectName,
        Integer sortOrder,
        Boolean isPrimary
) {
}
