package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import java.util.UUID;

public record ListingImageResponse(
        UUID uuid,
        String objectName,
        String uri,        // presigned, resolved at response time, never persisted
        Integer sortOrder,
        Boolean isPrimary
) {
}
