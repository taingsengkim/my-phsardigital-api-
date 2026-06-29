package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryTreeResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.UpdateCategoryRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    /**
     * Retrieves a paginated list of all categories.
     *
     * @param pageNumber the page number to retrieve (zero-based)
     * @param pageSize   the number of categories per page
     * @return a {@link Page} of {@link CategoryResponse} objects
     */
    Page<CategoryResponse> findAll(int pageNumber, int pageSize);

    /**
     * Creates a new category.
     *
     * @param categoryRequest the request payload containing category details
     * @return the created category as a {@link CategoryResponse}
     */
    CategoryResponse create(CategoryRequest categoryRequest);

    /**
     * Retrieves a category by its slug.
     *
     * @param categorySlug the unique slug identifying the category
     * @return the matching category as a {@link CategoryResponse}
     */
    CategoryResponse findBySlug(String categorySlug);

    /**
     * Retrieves a category by its unique identifier.
     *
     * @param id the UUID of the category
     * @return the matching category as a {@link CategoryResponse}
     */
    CategoryResponse findByUuid(UUID id);


    /**
     * Updates an existing category identified by its UUID.
     *
     * @param id                    the UUID of the category to update
     * @param updateCategoryRequest the request payload containing updated fields
     * @return the updated category as a {@link CategoryResponse}
     */
    CategoryResponse updateCategory(UUID id, UpdateCategoryRequest updateCategoryRequest);

    /**
     * Performs a soft delete on the category identified by its slug,
     * marking it as inactive/deleted without removing it from the database.
     *
     * @param slug the unique slug identifying the category to soft-delete
     */
    void softDelete(String slug);

    /**
     * Retrieves the direct child categories of a given parent category.
     *
     * @param uuid the UUID of the parent category
     * @return a list of child categories as {@link CategoryResponse} objects
     */
    List<CategoryResponse> findChildByUuid(UUID uuid);

    /**
     * Builds and retrieves the full category tree, representing the
     * hierarchical structure of categories and their nested children.
     *
     * @return a list of root-level {@link CategoryTreeResponse} objects,
     *         each containing nested child categories
     */
    List<CategoryTreeResponse> getCategoryTree();
}
