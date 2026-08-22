package co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto;

import java.util.UUID;

/**
 * @param value what a listing stores and what a facet filters on
 * @param label what a dropdown prints; equal to {@code value} when no label was set
 */
public record CategoryAttributeOptionResponse(
        UUID uuid,
        String value,
        String label,
        Integer sortOrder
) {
}
