package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeService;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeSchemaResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read model for managing taxonomy before it is public. The existing
 * {@code /api/v1/admin/**} security rule makes every route here administrator-only.
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Validated
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final CategoryAttributeService categoryAttributeService;

    /** Active and inactive categories; soft-deleted categories are intentionally absent. */
    @GetMapping
    public Page<CategoryResponse> findAll(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater") int pageNumber,
            @RequestParam(defaultValue = "25")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100") int pageSize) {
        return categoryService.findAllForAdmin(pageNumber, pageSize);
    }

    @GetMapping("/{uuid}")
    public CategoryResponse findByUuid(@PathVariable UUID uuid) {
        return categoryService.findByUuidForAdmin(uuid);
    }

    @GetMapping("/{uuid}/attributes")
    public CategoryAttributeSchemaResponse getSchema(
            @PathVariable UUID uuid,
            @RequestParam(defaultValue = "true") boolean includeInherited) {
        return categoryAttributeService.getSchemaForAdmin(uuid, includeInherited);
    }
}
