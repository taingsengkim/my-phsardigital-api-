package co.istad.sengkim.phsardigital.features.listing_images;

import jakarta.validation.constraints.NotBlank;

public record ListingImageRequest(
        @NotBlank(message = "Image URL must not be blank")
        String imageUrl,
        Integer sortOrder,
        Boolean isPrimary
) {
}
