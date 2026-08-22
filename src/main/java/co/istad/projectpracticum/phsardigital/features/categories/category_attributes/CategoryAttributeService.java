package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeRequest;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeSchemaResponse;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.UpdateCategoryAttributeRequest;

import java.util.List;
import java.util.UUID;

/**
 * Administers what a category expects of its listings. Reading a schema is public — the
 * storefront draws its listing form and its spec table from it; changing one is an
 * administrator's job, enforced by the path rules on {@code /api/v1/categories/**}.
 */
public interface CategoryAttributeService {

    /**
     * The category's schema, sectioned for display.
     *
     * @param includeInherited whether attributes declared on an ancestor are folded in.
     *                         True is what a storefront wants; false is the admin view of
     *                         "what does this category itself declare"
     */
    CategoryAttributeSchemaResponse getSchema(UUID categoryUuid, boolean includeInherited);

    /** The same, by the slug the storefront's URLs are built from. */
    CategoryAttributeSchemaResponse getSchemaBySlug(String categorySlug, boolean includeInherited);

    /**
     * Declares attributes on a category. Taken as a batch because a schema is authored
     * as a set — a phone's Display section is four attributes that only make sense
     * together — and because a partial failure halfway through a set is worse than none.
     */
    List<CategoryAttributeResponse> create(UUID categoryUuid, List<CategoryAttributeRequest> requests);

    /**
     * @param categoryUuid the category that declares it. An inherited attribute is edited
     *                     on the ancestor that owns it, not on the category that shows it
     */
    CategoryAttributeResponse update(UUID categoryUuid, UUID attributeUuid,
                                     UpdateCategoryAttributeRequest request);

    /**
     * Soft-deletes the attribute: listings already carrying it keep the value they were
     * saved with, it simply stops being offered, required or filterable.
     */
    void delete(UUID categoryUuid, UUID attributeUuid);
}
