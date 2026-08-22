package co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;

import java.util.List;
import java.util.UUID;

/**
 * One spec a category expects, as the "post a listing" form and the product page read it.
 *
 * @param code      the stable key a listing stores against, e.g. {@code screen_size}
 * @param group     the spec-table section, e.g. "Display"; null prints under "Other"
 * @param options   the permitted values, empty for anything but SELECT and MULTI_SELECT
 * @param inherited whether it comes from an ancestor rather than the category asked
 *                  about — an admin screen edits it where it is declared, not here
 * @param ownerCategorySlug which category declares it, which is where a PATCH must go
 */
public record CategoryAttributeResponse(
        UUID uuid,
        String code,
        String label,
        String group,
        AttributeDataType dataType,
        String unit,
        Boolean required,
        Boolean filterable,
        Double minValue,
        Double maxValue,
        Integer sortOrder,
        Integer groupSortOrder,
        List<CategoryAttributeOptionResponse> options,
        Boolean inherited,
        UUID ownerCategoryUuid,
        String ownerCategorySlug,
        String ownerCategoryName
) {

    /** The same definition, marked as reaching this category through its parent. */
    public CategoryAttributeResponse asInherited() {
        return new CategoryAttributeResponse(
                uuid, code, label, group, dataType, unit, required, filterable,
                minValue, maxValue, sortOrder, groupSortOrder, options,
                true, ownerCategoryUuid, ownerCategorySlug, ownerCategoryName);
    }
}
