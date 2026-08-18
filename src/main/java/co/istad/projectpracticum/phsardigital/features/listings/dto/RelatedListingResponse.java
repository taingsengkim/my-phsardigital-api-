package co.istad.projectpracticum.phsardigital.features.listings.dto;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategorySummaryResponse;
import co.istad.projectpracticum.phsardigital.features.listings.RelatedReason;

import java.util.UUID;

/**
 * One suggestion on a product page.
 *
 * <p>A card, not a listing: enough to draw the tile and link to it, and nothing more.
 * {@code ListingResponse} would have worked, but it carries the full description, the
 * whole gallery and every attribute — a strip of eight of those is a large response for
 * a row of thumbnails, and every field on it is one the client already fetches when the
 * buyer actually opens the product.
 *
 * @param stockQty how many are left, for the "only 2 left" badge; never zero, since
 *                 only listings still on sale are suggested
 * @param reason   which signal put this listing on the strip
 */
public record RelatedListingResponse(
        UUID uuid,
        String title,
        String slug,
        Double price,
        Integer stockQty,
        Integer sold,
        String thumbnailUri,
        CategorySummaryResponse category,
        String sellerId,
        String businessName,
        RelatedReason reason
) {
}
