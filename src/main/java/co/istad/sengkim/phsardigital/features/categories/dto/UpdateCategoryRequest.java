package co.istad.sengkim.phsardigital.features.categories.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

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

        @Min(value = 0, message = "Level must be 0 or greater")
        @Max(value = 10, message = "Level must not exceed 10")
        Integer level,
        Boolean isActive,
        UUID parentUuid
){
}
