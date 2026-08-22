package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto;

import java.util.List;

/**
 * One section of a product's spec table — "Display", and the four specs under it.
 *
 * @param name the section heading; "Other" for specs whose attribute named no group, and
 *             for the custom ones a seller added
 */
public record ListingSpecificationGroupResponse(
        String name,
        List<ListingAttributeResponse> attributes
) {
}
