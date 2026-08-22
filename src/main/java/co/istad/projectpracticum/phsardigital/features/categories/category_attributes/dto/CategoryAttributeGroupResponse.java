package co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto;

import java.util.List;

/**
 * One section of a category's schema — "Display" and the four attributes filed under it.
 *
 * @param name the section heading; "Other" for attributes that named no group
 */
public record CategoryAttributeGroupResponse(
        String name,
        List<CategoryAttributeResponse> attributes
) {
}
