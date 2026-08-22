package co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto;

import java.util.List;
import java.util.UUID;

/**
 * Everything a category expects of its listings, sectioned the way a product page prints
 * it and a listing form asks for it.
 *
 * <p>{@code attributes} is the same set flat, for callers that would only have to undo
 * the grouping.
 */
public record CategoryAttributeSchemaResponse(
        UUID categoryUuid,
        String categorySlug,
        String categoryName,
        List<CategoryAttributeGroupResponse> groups,
        List<CategoryAttributeResponse> attributes
) {
}
