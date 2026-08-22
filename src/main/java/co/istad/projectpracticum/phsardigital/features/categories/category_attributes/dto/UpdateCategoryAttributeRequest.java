package co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Every field is optional: null means "leave it alone".
 *
 * <p>Which is why there is no way to clear a unit or a bound here — those are the fields
 * where null is already the meaningful value, and a PATCH cannot tell "unset it" from
 * "don't touch it". Delete and re-create the attribute to drop one.
 *
 * @param options when given, replaces the whole option list rather than appending to it,
 *                so removing a discontinued size is one call
 */
public record UpdateCategoryAttributeRequest(

        @Size(max = 100, message = "Code must not exceed 100 characters")
        @Pattern(
                regexp = "^[a-z0-9]+(?:_[a-z0-9]+)*$",
                message = "Code must be lowercase alphanumeric with underscores (e.g. screen_size)"
        )
        String code,

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
