package co.istad.projectpracticum.phsardigital.features.listings.dto;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategorySummaryResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingSpecificationGroupResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ListingImageResponse;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ThumbnailImageResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param discountPrice the sale price, or null when the listing is not discounted. What
 *                      a buyer pays is this when present, {@code fullPrice} otherwise
 * @param isFavorite    whether the caller has saved this listing. Always false for an
 *                      anonymous visitor, who has nowhere to have saved it
 * @param listingAttributes every spec flat, in the order it was stored
 * @param specifications    the same specs sectioned the way the category declares them —
 *                          Display, then Performance, then Camera — which is the shape a
 *                          product page's spec table is drawn from
 */
public record ListingResponse(
        UUID uuid,
        SellerProfileSummaryResponse sellerProfile,
        CategorySummaryResponse category,
        String title,
        String slug,
        String description,
        String sku,
        BigDecimal costPrice,
        BigDecimal fullPrice,
        BigDecimal discountPrice,
        Integer stockQty,
        ListingStatus status,
        Boolean isFeatured,
        ThumbnailImageResponse thumbnailUri,
        Integer sold,
        List<ListingImageResponse> images,
        List<ListingAttributeResponse> listingAttributes ,
        List<ListingSpecificationGroupResponse> specifications,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt,
        Double averageRating,
        Long reviewCount,
        Boolean isFavorite
) {

    /**
     * The same listing with its star rating and the caller's saved-state filled in.
     *
     * <p>The mapper leaves all three null, so a listing embedded in something else — a
     * review carries one — costs no extra query. Null means "not computed on this route",
     * as it does on {@code SellerProfileResponse}; the listing's own routes fill them in
     * through {@code ListingResponseFactory}.
     */
    /**
     * The same listing with its cost price disclosed.
     *
     * <p>The field is left out of the mapping entirely and added back here, rather than
     * mapped and stripped for strangers. The difference matters: mapped-and-stripped
     * fails open, so the day somebody adds a route that forgets to strip it, every
     * shop's margins are published. This way a forgotten route returns null, which is
     * merely a missing number.
     */
    public ListingResponse withCostPrice(BigDecimal costPrice) {
        return new ListingResponse(
                uuid, sellerProfile, category, title, slug, description, sku, costPrice,
                fullPrice, discountPrice, stockQty,
                status, isFeatured, thumbnailUri, sold, images, listingAttributes, specifications,
                createdAt, lastModifiedAt, averageRating, reviewCount, isFavorite);
    }

    public ListingResponse withRating(Double averageRating, Long reviewCount, Boolean isFavorite) {
        return new ListingResponse(
                uuid, sellerProfile, category, title, slug, description, sku, costPrice,
                fullPrice, discountPrice, stockQty,
                status, isFeatured, thumbnailUri, sold, images, listingAttributes, specifications,
                createdAt, lastModifiedAt, averageRating, reviewCount, isFavorite);
    }
}