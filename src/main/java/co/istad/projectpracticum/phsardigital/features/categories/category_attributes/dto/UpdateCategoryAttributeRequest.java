package co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Every field is optional: null means "leave it alone". The code remains in the
 * contract for compatibility, but it is a stable identifier: sending the current code
 * is idempotent and trying to rename it is rejected.
 *
 * <p>Use the matching {@code clear...} flag to remove a nullable unit or numeric bound;
 * null by itself continues to mean "do not change it". Sending a value and its clear
 * flag together is rejected as ambiguous.
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
        @Pattern(regexp = "(?s).*\\S.*", message = "Label must not be blank")
        String label,

        @Size(max = 100, message = "Group must not exceed 100 characters")
        String group,

        AttributeDataType dataType,

        @Size(max = 20, message = "Unit must not exceed 20 characters")
        String unit,

        Boolean clearUnit,

        Boolean required,

        Boolean filterable,

        Double minValue,

        Boolean clearMinValue,

        Double maxValue,

        Boolean clearMaxValue,

        @Min(value = 0, message = "Sort order must be 0 or greater")
        Integer sortOrder,

        @Min(value = 0, message = "Group sort order must be 0 or greater")
        Integer groupSortOrder,

        @Valid
        List<CategoryAttributeOptionRequest> options
) {
}
