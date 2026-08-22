package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeRequest;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeSchemaResponse;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.UpdateCategoryAttributeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Hangs off {@code /api/v1/categories} deliberately: the path rules in
 * {@code SecurityConfig} already make every GET under it public and every write
 * administrator-only, which is exactly the split a category schema wants.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Validated
public class CategoryAttributeController {

    private final CategoryAttributeService categoryAttributeService;

    /**
     * What this category expects of its listings. The storefront draws both the listing
     * form and the product page's spec table from this.
     *
     * @param includeInherited defaults true, which is what a seller filling in the form
     *                         needs — an attribute declared on Electronics still has to
     *                         be answered for a phone. Pass false for the admin view of
     *                         what this category declares itself
     */
    @GetMapping("/{uuid}/attributes")
    public CategoryAttributeSchemaResponse getSchema(
            @PathVariable UUID uuid,
            @RequestParam(required = false, defaultValue = "true") boolean includeInherited) {
        return categoryAttributeService.getSchema(uuid, includeInherited);
    }

    /** The same schema by slug, mirroring {@code GET /api/v1/categories/slug/{slug}}. */
    @GetMapping("/slug/{slug}/attributes")
    public CategoryAttributeSchemaResponse getSchemaBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "true") boolean includeInherited) {
        return categoryAttributeService.getSchemaBySlug(slug, includeInherited);
    }

    /**
     * Declares attributes on the category. Takes a batch: a schema is authored as a set.
     *
     * <p>The {@code @Valid} sits on the element rather than the list, which is the only
     * form that actually reaches the records inside — and the reason the class carries
     * {@code @Validated}.
     */
    @PostMapping("/{uuid}/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CategoryAttributeResponse> create(
            @PathVariable UUID uuid,
            @RequestBody @NotEmpty(message = "At least one attribute is required")
            List<@Valid CategoryAttributeRequest> requests) {
        return categoryAttributeService.create(uuid, requests);
    }

    @PatchMapping("/{uuid}/attributes/{attributeUuid}")
    public CategoryAttributeResponse update(
            @PathVariable UUID uuid,
            @PathVariable UUID attributeUuid,
            @Valid @RequestBody UpdateCategoryAttributeRequest request) {
        return categoryAttributeService.update(uuid, attributeUuid, request);
    }

    @DeleteMapping("/{uuid}/attributes/{attributeUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid, @PathVariable UUID attributeUuid) {
        categoryAttributeService.delete(uuid, attributeUuid);
    }
}
