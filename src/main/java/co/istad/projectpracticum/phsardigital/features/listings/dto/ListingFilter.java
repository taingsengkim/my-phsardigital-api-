package co.istad.projectpracticum.phsardigital.features.listings.dto;

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
 */
public record ListingFilter(
        UUID categoryUuid,
        String categorySlug,
        String search,
        String sellerId,
        Double minPrice,
        Double maxPrice
) {

    public boolean isEmpty() {
        return categoryUuid == null
                && isBlank(categorySlug)
                && isBlank(search)
                && isBlank(sellerId)
                && minPrice == null
                && maxPrice == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
