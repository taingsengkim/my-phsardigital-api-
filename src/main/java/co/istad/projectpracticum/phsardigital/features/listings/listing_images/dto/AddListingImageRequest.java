package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param sortOrder optional; omitting it appends the image to the end of the gallery
 */
public record AddListingImageRequest(
        @NotBlank(message = "Object name must not be blank")
        String objectName,
        Integer sortOrder
) {}
