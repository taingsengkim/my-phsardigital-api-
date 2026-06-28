package co.istad.sengkim.phsardigital.features.listings.dto;

import co.istad.sengkim.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.sengkim.phsardigital.features.categories.dto.CategorySummaryResponse;
import co.istad.sengkim.phsardigital.features.listings.listing_images.dto.ListingImageResponse;
import co.istad.sengkim.phsardigital.features.listings.ListingStatus;
import co.istad.sengkim.phsardigital.features.seller.dto.SellerProfileSummaryResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ListingResponse(
        UUID uuid,
        SellerProfileSummaryResponse sellerProfile,
        CategorySummaryResponse category,
        String title,
        String slug,
        String description,
        Double price,
        Integer stockQty,
        ListingStatus status,
        Boolean isFeatured,
        String thumbnailUri,
        Integer soldCount,
        List<ListingImageResponse> imagesList,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {
}