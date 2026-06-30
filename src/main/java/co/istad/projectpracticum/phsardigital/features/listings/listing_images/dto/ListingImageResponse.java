package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;

import java.util.UUID;

public record ListingImageResponse(
        UUID uuid,
        String uri,
        String objectName,
        Integer sortOrder,
        Boolean isPrimary
) {
}
