package co.istad.sengkim.phsardigital.features.categories;

import co.istad.sengkim.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.sengkim.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.sengkim.phsardigital.features.categories.dto.CategoryTreeResponse;
import co.istad.sengkim.phsardigital.features.categories.dto.UpdateCategoryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;
    @GetMapping
    public Page<CategoryResponse> allCategories(@RequestParam(required = false,defaultValue ="0") int pageNumber,
                                                @RequestParam(required = false,defaultValue ="25") int pageSize){
        return categoryService.findAll(pageNumber,pageSize);
    }
    @GetMapping("/{uuid}/children")
    public List<CategoryResponse> getCategoryChildren(@PathVariable UUID uuid){
        return categoryService.findChildByUuid(uuid);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse addCategory(@Valid @RequestBody CategoryRequest categoryRequest){
        return categoryService.createCategory(categoryRequest);
    }
    @GetMapping("/slug/{slug}")
    public CategoryResponse categoryBySlug(@PathVariable String slug){
        return categoryService.findBySlug(slug);
    }
    @GetMapping("/{uuid}")
    public CategoryResponse categoryById(@PathVariable UUID uuid){
        return categoryService.findByUuid(uuid);
    }

    @PatchMapping("/{uuid}")
    public CategoryResponse updateCategory(@PathVariable UUID uuid,@Valid @RequestBody UpdateCategoryRequest updateCategoryRequest){
        return categoryService.updateCategory(uuid,updateCategoryRequest);
    }
    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDeleteCategory(@PathVariable String slug){
         categoryService.softDeleteCategory(slug);
    }

    @GetMapping("/tree")
    public List<CategoryTreeResponse> getTree() {
        return categoryService.getCategoryTree();
    }
}
