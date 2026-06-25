package co.istad.sengkim.phsardigital.features.listings.dto;

import co.istad.sengkim.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.sengkim.phsardigital.features.listing_images.dto.ListingImageResponse;
import co.istad.sengkim.phsardigital.features.listings.ListingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListingResponse(
        UUID uuid,
        String sellerId,
        CategoryResponse category,
        String title,
        String slug,
        String description,
        Double price,
        Integer stockQty,
        ListingStatus status,
        Boolean isFeatured,
        String thumbnailUrl,
        Integer soldCount,
        List<ListingImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
}