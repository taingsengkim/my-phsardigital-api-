package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One spec as a seller sends it.
 *
 * @param key   matched against the category's attribute codes, tolerantly — {@code
 *              screen_size}, "screen-size" and "Screen size" all find the same
 *              definition. A key the category does not define is kept as a custom spec
 * @param value checked and normalised against that definition's type, options and bounds
 */
public record ListingAttributeCreateRequest(

        @NotBlank(message = "Attribute key is required")
        @Size(max = 100, message = "Attribute key must not exceed 100 characters")
        String key,

        @NotBlank(message = "Attribute value is required")
        @Size(max = 100, message = "Attribute value must not exceed 100 characters")
        String value,

        @Min(value = 0, message = "Sort order must be 0 or greater")
        Integer sortOrder
) {
}
