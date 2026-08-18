package co.istad.projectpracticum.phsardigital.features.listings;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

/**
 * The pieces the public browse query is assembled from. Specifications rather than one
 * JPQL query with {@code :param IS NULL} guards throughout: half a dozen optional
 * filters make that unreadable, and an untyped null bound into a Postgres comparison is
 * its own class of problem.
 */
final class ListingSpecifications {

    private ListingSpecifications() {
    }

    /**
     * The floor under every public query: on sale, from a shop that is still trading.
     * Suspending a shop leaves its listings {@code ACTIVE}, so without the shop check a
     * suspended seller's products stay in search and stay purchasable.
     */
    static Specification<Listing> publiclyBrowsable() {
        return (root, query, builder) -> builder.and(
                builder.equal(root.get("status"), ListingStatus.ACTIVE),
                builder.isTrue(root.get("sellerProfile").get("isActive")));
    }

    /**
     * Matches any of the given categories — the one named plus its descendants, so
     * browsing a parent shows what is filed under its children rather than an empty
     * shelf.
     */
    static Specification<Listing> inCategories(Collection<UUID> categoryUuids) {
        return (root, query, builder) -> root.get("category").get("uuid").in(categoryUuids);
    }

    /**
     * Title or description, case-insensitive, anywhere in the field.
     *
     * <p>A {@code LIKE '%term%'} cannot use a plain B-tree index. Fine at this size, and
     * the alternative is full-text search infrastructure — but this is the line to
     * revisit when the catalogue gets big.
     */
    static Specification<Listing> matching(String search) {
        return (root, query, builder) -> {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return builder.or(
                    builder.like(builder.lower(root.get("title")), pattern),
                    builder.like(builder.lower(root.get("description")), pattern));
        };
    }

    static Specification<Listing> soldBy(String sellerId) {
        return (root, query, builder) ->
                builder.equal(root.get("sellerProfile").get("sellerId"), sellerId);
    }

    static Specification<Listing> pricedAtLeast(Double minPrice) {
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    static Specification<Listing> pricedAtMost(Double maxPrice) {
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get("price"), maxPrice);
    }
}
