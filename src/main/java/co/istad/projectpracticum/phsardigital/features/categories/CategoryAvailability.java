package co.istad.projectpracticum.phsardigital.features.categories;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

/**
 * The shared category availability and lineage rule.
 *
 * <p>A category is public only when it and every ancestor are active and not deleted.
 * The bounded, cycle-aware walk deliberately fails closed: corrupt hierarchy data must
 * hide a catalogue branch rather than expose it or hang a request.
 */
@Component
public class CategoryAvailability {

    public static final int MAX_HIERARCHY_DEPTH = 20;

    /** Whether this category belongs in the public catalogue. */
    public boolean isEffectivelyActive(Category category) {
        if (category == null) {
            return false;
        }

        try {
            walk(category, true);
            return true;
        } catch (InvalidHierarchyException exception) {
            return false;
        }
    }

    /**
     * Returns the actual position in the parent chain (a root is level 1), rejecting
     * cycles and chains beyond the supported depth rather than trusting a stale
     * denormalised {@link Category#getLevel()} value.
     */
    public int depthOf(Category category) {
        return category == null ? 0 : walk(category, false);
    }

    private int walk(Category category, boolean requireAvailable) {
        Set<UUID> ids = new HashSet<>();
        Set<Category> transientNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        int depth = 0;

        for (Category current = category; current != null; current = current.getParentCategory()) {
            depth++;
            if (depth > MAX_HIERARCHY_DEPTH) {
                throw new InvalidHierarchyException(
                        "Category hierarchy exceeds the maximum depth of " + MAX_HIERARCHY_DEPTH + ".");
            }

            UUID uuid = current.getUuid();
            boolean firstVisit = uuid == null ? transientNodes.add(current) : ids.add(uuid);
            if (!firstVisit) {
                throw new InvalidHierarchyException("Category hierarchy contains a cycle.");
            }

            if (requireAvailable
                    && (Boolean.TRUE.equals(current.getIsDeleted())
                    || !Boolean.TRUE.equals(current.getIsActive()))) {
                throw new InvalidHierarchyException("Category hierarchy is not active.");
            }
        }
        return depth;
    }

    public static final class InvalidHierarchyException extends IllegalStateException {
        public InvalidHierarchyException(String message) {
            super(message);
        }
    }
}
