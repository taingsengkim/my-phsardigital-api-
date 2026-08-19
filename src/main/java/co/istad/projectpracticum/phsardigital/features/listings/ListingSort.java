package co.istad.projectpracticum.phsardigital.features.listings;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Parses the {@code sort=field,direction} parameter on the public catalogue.
 *
 * <p>The allowlist is the point: Spring Data resolves a raw client string against the
 * entity, so {@code sort=sellerProfile.suspensionReason} would sort by a moderation
 * note, and a typo would 500 from inside the repository rather than 400.
 */
final class ListingSort {

    /** Default order, matching what the endpoint answered before sorting existed. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "lastModifiedAt");

    /**
     * What a client may sort by, mapped to the entity attribute behind it. {@code price}
     * still resolves, to the list price.
     *
     * <p>Which means a discounted listing sorts by its list price, not by what it costs:
     * a {@code Sort} names one property and the effective price is a {@code COALESCE}
     * over two. The price filters do compare the effective price — see
     * {@link ListingSpecifications}.
     */
    private static final Map<String, String> SORTABLE = Map.of(
            "price", "fullPrice",
            "fullPrice", "fullPrice",
            "discountPrice", "discountPrice",
            "title", "title",
            "sold", "sold",
            "createdAt", "createdAt",
            "lastModifiedAt", "lastModifiedAt");

    private ListingSort() {
    }

    static Sort parse(String sort) {
        if (sort == null || sort.isBlank()) {
            return NEWEST_FIRST;
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();

        String attribute = SORTABLE.get(property);
        if (attribute == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot sort listings by '" + property + "'. Sortable fields: "
                            + String.join(", ", SORTABLE.keySet().stream().sorted().toList()) + ".");
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2 && !parts[1].isBlank()) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sort direction must be 'asc' or 'desc', not '" + parts[1].trim() + "'."));
        }
        return Sort.by(direction, attribute);
    }
}
