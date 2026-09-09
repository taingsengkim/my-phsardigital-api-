package co.istad.projectpracticum.phsardigital.features.purchases;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves {@code sortBy}/{@code sortOrder} on the admin orders table.
 *
 * <p>An allowlist for the same reason {@code ListingSort} has one: Spring Data resolves a
 * raw client string straight against the entity, so an unchecked value can sort by a
 * field nobody meant to expose, and a typo fails inside the repository as a 500 rather
 * than at the edge as a 400.
 */
final class AdminPurchaseSort {

    /**
     * Ties broken by the order's own id, so paging is stable. Without it two orders
     * placed in the same second can swap places between page 1 and page 2, and the reader
     * either sees one twice or never sees it.
     */
    private static final Sort TIE_BREAK = Sort.by(Sort.Direction.DESC, "uuid");

    /** Newest first — what an orders queue is read in, and the default nearly every request takes. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(TIE_BREAK);

    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "totalAmount", "totalPrice",
            "totalPrice", "totalPrice",
            "status", "status",
            "confirmedAt", "confirmedAt",
            "completedAt", "completedAt");

    private AdminPurchaseSort() {
    }

    static Sort parse(String sortBy, String sortOrder) {
        if (sortBy == null || sortBy.isBlank()) {
            return NEWEST_FIRST;
        }

        String attribute = SORTABLE.get(sortBy.trim());
        if (attribute == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot sort orders by '" + sortBy.trim() + "'. Sortable fields: "
                            + String.join(", ", SORTABLE.keySet().stream().sorted().toList()) + ".");
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (sortOrder != null && !sortOrder.isBlank()) {
            direction = Sort.Direction.fromOptionalString(sortOrder.trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sort order must be 'asc' or 'desc', not '" + sortOrder.trim() + "'."));
        }

        return Sort.by(direction, attribute).and(TIE_BREAK);
    }
}
