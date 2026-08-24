package co.istad.projectpracticum.phsardigital.features.listings;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttribute;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
                builder.greaterThan(root.get("stockQty"), 0),
                builder.isTrue(root.get("sellerProfile").get("isActive")),
                builder.isTrue(root.get("category").get("isActive")),
                builder.isFalse(root.get("category").get("isDeleted")));
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

    /**
     * Both price filters compare what the buyer would actually pay: capping a search at
     * $10 is asking to see a $12 product discounted to $10. The coalesce is what keeps
     * undiscounted listings in the results at all — {@code null >= 5} is unknown, not
     * false.
     */
    static Specification<Listing> pricedAtLeast(BigDecimal minPrice) {
        return (root, query, builder) ->
                builder.greaterThanOrEqualTo(effectivePrice(root, builder), minPrice);
    }

    static Specification<Listing> pricedAtMost(BigDecimal maxPrice) {
        return (root, query, builder) ->
                builder.lessThanOrEqualTo(effectivePrice(root, builder), maxPrice);
    }

    private static Expression<BigDecimal> effectivePrice(Root<Listing> root, CriteriaBuilder builder) {
        return builder.coalesce(root.get("discountPrice"), root.get("fullPrice"));
    }

    /**
     * Listings carrying this attribute at one of these values — the facet panel's "8 GB
     * or 12 GB of RAM". Scalar answers compare against {@code value}; MULTI_SELECT
     * answers compare against their individually stored {@code selectedValues}, so
     * choosing Black also finds a listing displayed as "Black, White".
     *
     * <p>An {@code EXISTS} subquery rather than a join: a listing has many attributes, and
     * joining them would return the same listing once per matching row, which breaks the
     * page count before it breaks anything else. Two facets mean two subqueries, which is
     * also what makes them narrow together instead of fighting over one join.
     *
     * <p>Both sides are lowercased, so a facet works whatever case the link was built in.
     * Stored values are normalised on the way in, so this compares like with like.
     */
    static Specification<Listing> hasAttribute(String key, Collection<String> values) {
        List<String> wanted = values.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        String wantedKey = key.toLowerCase(Locale.ROOT);

        return (root, query, builder) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<ListingAttribute> attribute = subquery.from(ListingAttribute.class);
            SetJoin<ListingAttribute, String> selectedValue =
                    attribute.joinSet("selectedValues", JoinType.LEFT);
            // A custom scalar attribute has no definition. This must be a LEFT join;
            // navigating the nullable association as an implicit inner join would make
            // the scalar branch below silently stop matching custom attributes.
            Join<ListingAttribute, CategoryAttribute> definition =
                    attribute.join("definition", JoinType.LEFT);
            Expression<String> compactDisplayValue = builder.function(
                    "replace", String.class,
                    builder.lower(attribute.get("value")),
                    builder.literal(", "),
                    builder.literal(","));
            Expression<String> paddedDisplayValue = builder.concat(
                    builder.concat(",", compactDisplayValue), ",");
            Predicate legacyMultiSelectMatch = builder.and(
                    builder.equal(definition.get("dataType"),
                            AttributeDataType.MULTI_SELECT),
                    builder.or(wanted.stream()
                            .map(value -> builder.like(
                                    paddedDisplayValue,
                                    "%," + escapeLike(value) + ",%",
                                    '\\'))
                            .toArray(Predicate[]::new)));
            subquery.select(attribute.get("listing").get("uuid"));
            subquery.where(builder.and(
                    builder.equal(attribute.get("listing").get("uuid"), root.get("uuid")),
                    builder.equal(builder.lower(attribute.get("key")), wantedKey),
                    builder.or(
                            builder.lower(attribute.get("value")).in(wanted),
                            builder.lower(selectedValue).in(wanted),
                            legacyMultiSelectMatch)));
            return builder.exists(subquery);
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
