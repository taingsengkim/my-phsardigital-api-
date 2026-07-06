package co.istad.projectpracticum.phsardigital.features.listings.dto;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategorySummaryResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ListingImageResponse;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ThumbnailImageResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileSummaryResponse;

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
        ThumbnailImageResponse thumbnailUri,
        Integer sold,
        List<ListingImageResponse> images,
        List<ListingAttributeResponse> listingAttributes ,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {
}