package co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryAttributeOptionRequest(

        @NotBlank(message = "Option value is required")
        @Size(max = 100, message = "Option value must not exceed 100 characters")
        String value,

        @Size(max = 150, message = "Option label must not exceed 150 characters")
        String label,

        @Min(value = 0, message = "Sort order must be 0 or greater")
        Integer sortOrder
) {
}
