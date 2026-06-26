package co.istad.sengkim.phsardigital.features.categories;

import co.istad.sengkim.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.sengkim.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.sengkim.phsardigital.features.categories.dto.CategoryTreeResponse;
import co.istad.sengkim.phsardigital.features.categories.dto.UpdateCategoryRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    Page<CategoryResponse> findAll(int pageNumber, int pageSize);

    CategoryResponse create(CategoryRequest categoryRequest);

    CategoryResponse findBySlug(String categorySlug);

    CategoryResponse findByUuid(UUID id);

    CategoryResponse updateCategory(UUID id, UpdateCategoryRequest updateCategoryRequest);

    void softDelete(String slug);

    List<CategoryResponse> findChildByUuid(UUID uuid);

    List<CategoryTreeResponse> getCategoryTree();
}
