package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryTreeResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.UpdateCategoryRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Validated
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public Page<CategoryResponse> findAll(
            @RequestParam(required = false, defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int pageNumber,
            @RequestParam(required = false, defaultValue = "25")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize){
        return categoryService.findAll(pageNumber,pageSize);
    }

    @GetMapping("/{uuid}/children")
    public List<CategoryResponse> getChildren(@PathVariable UUID uuid){
        return categoryService.findChildByUuid(uuid);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest categoryRequest){
        return categoryService.create(categoryRequest);
    }

    @GetMapping("/slug/{slug}")
    public CategoryResponse findBySlug(@PathVariable String slug){
        return categoryService.findBySlug(slug);
    }

    @GetMapping("/{uuid}")
    public CategoryResponse findById(@PathVariable UUID uuid){
        return categoryService.findByUuid(uuid);
    }

    @PatchMapping("/{uuid}")
    public CategoryResponse update(@PathVariable UUID uuid,@Valid @RequestBody UpdateCategoryRequest updateCategoryRequest){
        return categoryService.updateCategory(uuid,updateCategoryRequest);
    }

    @DeleteMapping("/{uuid}/icon")
    public CategoryResponse removeIcon(@PathVariable UUID uuid){
        return categoryService.removeIcon(uuid);
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@PathVariable String slug){
         categoryService.softDelete(slug);
    }

    @GetMapping("/tree")
    public List<CategoryTreeResponse> getTree() {
        return categoryService.getCategoryTree();
    }
}
