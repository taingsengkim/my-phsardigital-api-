package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import java.util.UUID;

/**
 * @param sortOrder position in the gallery; images arrive already in this order, so a
 *                  client only needs it to send a reorder back
 */
public record ListingImageResponse(
        UUID uuid,
        String uri,
        String objectName,
        Integer sortOrder
) {
}
