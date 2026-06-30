package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import jakarta.validation.constraints.NotBlank;

public record AddListingImageRequest(
        @NotBlank(message = "Object name must not be blank")
        String objectName,
        Integer sortOrder,
        Boolean isPrimary
) {}