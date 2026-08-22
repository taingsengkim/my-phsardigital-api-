package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingSpecificationGroupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a listing's flat attribute rows into the sectioned spec table a product page
 * prints — Display, then Performance, then Camera — using the order the category declared
 * rather than whatever order the seller happened to type them in.
 */
@Component
@RequiredArgsConstructor
public class ListingSpecificationAssembler {

    /** The heading for specs whose attribute named no group, and for custom ones. */
    static final String UNGROUPED = "Other";

    private final ListingAttributeMapper listingAttributeMapper;

    public List<ListingSpecificationGroupResponse> assemble(List<ListingAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return List.of();
        }

        // A group holds together on the lowest rank any of its members carries — the
        // members can disagree once some are inherited from an ancestor category.
        Map<String, Integer> groupRank = new HashMap<>();
        for (ListingAttribute attribute : attributes) {
            groupRank.merge(groupOf(attribute), rankOf(attribute), Integer::min);
        }

        List<ListingAttribute> ordered = new ArrayList<>(attributes);
        ordered.sort(Comparator
                .comparingInt((ListingAttribute a) -> groupRank.getOrDefault(groupOf(a), Integer.MAX_VALUE))
                .thenComparing(ListingSpecificationAssembler::groupOf)
                .thenComparingInt(ListingSpecificationAssembler::positionOf)
                .thenComparing(a -> a.getKey() == null ? "" : a.getKey()));

        Map<String, List<ListingAttributeResponse>> grouped = new LinkedHashMap<>();
        for (ListingAttribute attribute : ordered) {
            grouped.computeIfAbsent(groupOf(attribute), key -> new ArrayList<>())
                    .add(listingAttributeMapper.toResponse(attribute));
        }

        return grouped.entrySet().stream()
                .map(entry -> new ListingSpecificationGroupResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static String groupOf(ListingAttribute attribute) {
        CategoryAttribute definition = attribute.getDefinition();
        if (definition == null || definition.getGroupName() == null || definition.getGroupName().isBlank()) {
            return UNGROUPED;
        }
        return definition.getGroupName();
    }

    /** Ungrouped specs sink to the bottom; a declared group keeps the rank it was given. */
    private static int rankOf(ListingAttribute attribute) {
        CategoryAttribute definition = attribute.getDefinition();
        if (UNGROUPED.equals(groupOf(attribute)) || definition == null) {
            return Integer.MAX_VALUE;
        }
        return definition.getGroupSortOrder();
    }

    /**
     * Position inside the group. The category's order wins where there is one, so two
     * sellers' phones print their Display sections the same way round; a custom spec falls
     * back to the order the seller gave it.
     */
    private static int positionOf(ListingAttribute attribute) {
        CategoryAttribute definition = attribute.getDefinition();
        if (definition != null) {
            return definition.getSortOrder();
        }
        return attribute.getSortOrder() == null ? 0 : attribute.getSortOrder();
    }
}
