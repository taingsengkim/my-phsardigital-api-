package co.istad.projectpracticum.phsardigital.features.categories.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Partial update: only non-null fields are applied. {@code level} is absent
 * because it follows the parent — reparenting recalculates it for the whole
 * subtree. Use {@code DELETE /{uuid}/icon} to clear an icon, since a null
 * {@code iconFileId} here means "leave it alone".
 */
public record UpdateCategoryRequest(
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @Size(max = 150, message = "Slug must not exceed 150 characters")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug must be lowercase alphanumeric with hyphens (e.g. my-category)"
        )
        String slug,

        UUID iconFileId,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Min(value = 0, message = "Sort order must be 0 or greater")
        Integer sortOrder,

        Boolean isActive,

        UUID parentUuid
){
}
