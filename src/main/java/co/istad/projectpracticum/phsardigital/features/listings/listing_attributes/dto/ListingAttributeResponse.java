package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto;

import java.util.UUID;

public record ListingAttributeResponse(
        UUID uuid,
        String key,
        String value,
        Integer sortOrder,
        UUID listingUuid
) {
}
