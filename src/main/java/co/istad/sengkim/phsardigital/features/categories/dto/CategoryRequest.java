package co.istad.sengkim.phsardigital.features.categories.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;
public record CategoryRequest (
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Slug is required")
        @Size(max = 150, message = "Slug must not exceed 150 characters")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug must be lowercase alphanumeric with hyphens (e.g. my-category)"
        )
        String slug,

        UUID iconFileId,
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @NotNull(message = "Level is required")
        @Min(value = 0, message = "Level must be 0 or greater")
        @Max(value = 10, message = "Level must not exceed 10")
        Integer level,
        @NotNull(message = "isActive is required")
        Boolean isActive,
        UUID parentUuid
){
}
