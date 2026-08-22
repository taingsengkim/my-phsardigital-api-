package co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param code     the key listings store against. Constrained to the same shape as a
 *                 slug so it survives a URL, a query parameter and a JSON key unescaped
 * @param dataType defaults to {@code TEXT}
 * @param options  required for SELECT and MULTI_SELECT, rejected for anything else —
 *                 a number with a fixed list of legal values is a SELECT
 */
public record CategoryAttributeRequest(

        @NotBlank(message = "Code is required")
        @Size(max = 100, message = "Code must not exceed 100 characters")
        @Pattern(
                regexp = "^[a-z0-9]+(?:_[a-z0-9]+)*$",
                message = "Code must be lowercase alphanumeric with underscores (e.g. screen_size)"
        )
        String code,

        @NotBlank(message = "Label is required")
        @Size(max = 150, message = "Label must not exceed 150 characters")
        String label,

        @Size(max = 100, message = "Group must not exceed 100 characters")
        String group,

        AttributeDataType dataType,

        @Size(max = 20, message = "Unit must not exceed 20 characters")
        String unit,

        Boolean required,

        Boolean filterable,

        Double minValue,

        Double maxValue,

        @Min(value = 0, message = "Sort order must be 0 or greater")
        Integer sortOrder,

        @Min(value = 0, message = "Group sort order must be 0 or greater")
        Integer groupSortOrder,

        @Valid
        List<CategoryAttributeOptionRequest> options
) {
}
