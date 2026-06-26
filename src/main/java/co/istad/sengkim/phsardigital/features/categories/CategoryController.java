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
    public Page<CategoryResponse> findAll(@RequestParam(required = false,defaultValue ="0") int pageNumber,
                                                @RequestParam(required = false,defaultValue ="25") int pageSize){
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
