package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param sortOrder optional; omitting it keeps the images in the order they were sent
 */
public record ListingImageRequest(
        @NotBlank(message = "Object name must not be blank")
        String objectName,
        Integer sortOrder
) {
}
