package co.istad.projectpracticum.phsardigital.features.listings.dto;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategorySummaryResponse;
import co.istad.projectpracticum.phsardigital.features.listings.RelatedReason;

import java.util.UUID;

/**
 * One suggestion on a product page: a card, not a listing. {@code ListingResponse}
 * carries the full description, the whole gallery and every attribute, which is a lot
 * of response for a row of thumbnails.
 *
 * @param reason which signal put this listing on the strip
 */
public record RelatedListingResponse(
        UUID uuid,
        String title,
        String slug,
        BigDecimal fullPrice,
        BigDecimal discountPrice,
        Integer stockQty,
        Integer sold,
        String thumbnailUri,
        CategorySummaryResponse category,
        String sellerId,
        String businessName,
        RelatedReason reason
) {
}
