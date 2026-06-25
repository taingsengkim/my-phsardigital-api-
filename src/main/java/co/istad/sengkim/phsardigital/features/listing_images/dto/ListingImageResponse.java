package co.istad.sengkim.phsardigital.features.listing_images.dto;

import java.util.UUID;

public record ListingImageResponse(
        UUID uuid,
        String imageUrl,
        Integer sortOrder,
        Boolean isPrimary
) {
}
