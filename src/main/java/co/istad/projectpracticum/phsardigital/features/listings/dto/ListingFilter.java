package co.istad.projectpracticum.phsardigital.features.listings.dto;

import java.util.List;
import java.util.UUID;

/**
 * What a shopper is narrowing the catalogue by. Every field is optional; an absent one
 * simply does not constrain the query.
 *
 * @param categoryUuid  a category, matched together with everything beneath it
 * @param categorySlug  the same, named the way the storefront's URLs name it; ignored
 *                      when {@code categoryUuid} is given
 * @param search        matched against title and description, case-insensitively
 * @param sellerId      one shop's window
 * @param minPrice      inclusive lower bound
 * @param maxPrice      inclusive upper bound
 * @param attributes    facets from the category's schema — 8 GB of RAM, an AMOLED panel.
 *                      Each narrows the search further; see {@link AttributeFilter}
 */
public record ListingFilter(
        UUID categoryUuid,
        String categorySlug,
        String search,
        String sellerId,
        Double minPrice,
        Double maxPrice,
        List<AttributeFilter> attributes
) {

    /** The filter as it was before facets existed, for callers that do not use them. */
    public ListingFilter(UUID categoryUuid, String categorySlug, String search,
                         String sellerId, Double minPrice, Double maxPrice) {
        this(categoryUuid, categorySlug, search, sellerId, minPrice, maxPrice, List.of());
    }

    public boolean isEmpty() {
        return categoryUuid == null
                && isBlank(categorySlug)
                && isBlank(search)
                && isBlank(sellerId)
                && minPrice == null
                && maxPrice == null
                && (attributes == null || attributes.isEmpty());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
