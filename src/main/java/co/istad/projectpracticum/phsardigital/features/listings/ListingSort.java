package co.istad.projectpracticum.phsardigital.features.listings;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

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

    private static final Set<String> SORTABLE =
            Set.of("price", "title", "sold", "createdAt", "lastModifiedAt");

    private ListingSort() {
    }

    static Sort parse(String sort) {
        if (sort == null || sort.isBlank()) {
            return NEWEST_FIRST;
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();

        if (!SORTABLE.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot sort listings by '" + property + "'. Sortable fields: "
                            + String.join(", ", SORTABLE.stream().sorted().toList()) + ".");
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2 && !parts[1].isBlank()) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sort direction must be 'asc' or 'desc', not '" + parts[1].trim() + "'."));
        }
        return Sort.by(direction, property);
    }
}
