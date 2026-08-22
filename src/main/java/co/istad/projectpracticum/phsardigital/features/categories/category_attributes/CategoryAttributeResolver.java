package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Works out the attributes a category actually has, which is rarely just the ones filed
 * against it: a definition on Electronics applies to every phone and laptop underneath.
 *
 * <p>The nearest declaration wins. Phones may narrow an inherited "brand" to a dropdown
 * of the brands it stocks by declaring its own {@code brand}, and the Electronics one
 * stops applying there without being touched.
 */
@Component
@RequiredArgsConstructor
public class CategoryAttributeResolver {

    /** How deep the walk to the root may go before a cycle in the data is assumed. */
    private static final int MAX_DEPTH = 20;

    private final CategoryAttributeRepository categoryAttributeRepository;

    /**
     * The category's effective schema, in the order a spec table prints it: by group,
     * then by position within the group.
     */
    public List<CategoryAttribute> effectiveFor(Category category) {
        return sorted(nearestWins(category));
    }

    /** Only what this category declares itself, ignoring anything inherited. */
    public List<CategoryAttribute> declaredBy(Category category) {
        return sorted(categoryAttributeRepository.findAllByCategory_UuidAndIsDeletedFalse(category.getUuid()));
    }

    /**
     * A schema keyed for lookup, by normalised code and — so a seller may send
     * "Screen size" as readily as {@code screen_size} — by normalised label too. Codes are
     * inserted last and therefore win any collision with a label.
     *
     * <p>Takes the resolved list rather than the category, so a caller that needs both
     * the lookup and the list itself pays for one resolution instead of two.
     */
    public static Map<String, CategoryAttribute> lookup(Collection<CategoryAttribute> attributes) {
        Map<String, CategoryAttribute> lookup = new HashMap<>();
        for (CategoryAttribute attribute : attributes) {
            lookup.put(normaliseKey(attribute.getLabel()), attribute);
        }
        for (CategoryAttribute attribute : attributes) {
            lookup.put(normaliseKey(attribute.getCode()), attribute);
        }
        return lookup;
    }

    /**
     * The form a key is compared in: lowercase, with spaces and hyphens folded to the
     * underscores codes are written with, so "Screen Size", "screen-size" and
     * {@code screen_size} all find the same definition.
     */
    public static String normaliseKey(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    /**
     * Walks from the category to the root, keeping the first definition seen for each
     * code — the first being the nearest, since the walk starts at the category itself.
     */
    private List<CategoryAttribute> nearestWins(Category category) {
        List<UUID> branch = branchToRoot(category);
        if (branch.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> distance = new HashMap<>();
        for (int i = 0; i < branch.size(); i++) {
            distance.put(branch.get(i), i);
        }

        Map<String, CategoryAttribute> byCode = new LinkedHashMap<>();
        for (CategoryAttribute attribute : categoryAttributeRepository
                .findAllByCategory_UuidInAndIsDeletedFalse(branch)) {
            CategoryAttribute held = byCode.get(attribute.getCode());
            if (held == null || distance.get(attribute.getCategory().getUuid())
                    < distance.get(held.getCategory().getUuid())) {
                byCode.put(attribute.getCode(), attribute);
            }
        }
        return new ArrayList<>(byCode.values());
    }

    /** The category, then its parent, then its parent's parent — nearest first. */
    private List<UUID> branchToRoot(Category category) {
        Set<UUID> branch = new LinkedHashSet<>();
        Category current = category;
        // add() returning false means this node has already been seen, which only
        // happens if the parent chain loops — bail rather than walk it forever.
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (!branch.add(current.getUuid())) {
                break;
            }
            current = current.getParentCategory();
        }
        return new ArrayList<>(branch);
    }

    /**
     * Groups keep the lowest sort order any of their members carries, so a group holds
     * together even when its attributes disagree about where the group belongs — which
     * they will, once half of them are inherited from a category that never heard of the
     * other half.
     */
    private List<CategoryAttribute> sorted(List<CategoryAttribute> attributes) {
        Map<String, Integer> groupRank = new HashMap<>();
        for (CategoryAttribute attribute : attributes) {
            // Ungrouped ones sink to the bottom: they are the leftovers a spec table
            // prints under "Other", never the section it opens with.
            int rank = groupKey(attribute).isEmpty() ? Integer.MAX_VALUE : attribute.getGroupSortOrder();
            groupRank.merge(groupKey(attribute), rank, Integer::min);
        }

        List<CategoryAttribute> ordered = new ArrayList<>(attributes);
        ordered.sort(Comparator
                .comparingInt((CategoryAttribute a) -> groupRank.getOrDefault(groupKey(a), 0))
                .thenComparing(CategoryAttributeResolver::groupKey)
                .thenComparingInt(CategoryAttribute::getSortOrder)
                .thenComparing(CategoryAttribute::getLabel));
        return ordered;
    }

    /** Ungrouped attributes share one bucket rather than each becoming a group of one. */
    private static String groupKey(CategoryAttribute attribute) {
        String group = attribute.getGroupName();
        return group == null || group.isBlank() ? "" : group;
    }
}
