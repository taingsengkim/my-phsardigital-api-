package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryTreeResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.UpdateCategoryRequest;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService{

    /** Curated order first, then alphabetical, so listings are stable. */
    private static final Sort DISPLAY_ORDER =
            Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name"));

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final FileUploadRepository fileUploadRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAll(int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, DISPLAY_ORDER);
        return categoryRepository.findAllByIsDeletedFalse(pageable)
                .map(categoryMapper::toCategoryResponse);
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest categoryRequest) {
        if (categoryRepository.existsByName(categoryRequest.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category with this name already exists.");
        }
        if (categoryRepository.existsBySlug(categoryRequest.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category with this slug already exists.");
        }

        Category category = categoryMapper.toCategory(categoryRequest);

        Category parent = categoryRequest.parentUuid() == null
                ? null
                : activeCategoryOr404(categoryRequest.parentUuid(), "Parent category not found");
        category.setParentCategory(parent);
        category.setLevel(levelUnder(parent));

        // The icon was silently dropped here before, so a category could only get
        // one through a follow-up PATCH.
        category.setIconFile(resolveIcon(categoryRequest.iconFileId()));
        category.setSortOrder(categoryRequest.sortOrder() == null ? 0 : categoryRequest.sortOrder());
        category.setIsDeleted(false);

        return categoryMapper.toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findBySlug(String categorySlug) {
        Category category = categoryRepository.findBySlugAndIsDeletedFalse(categorySlug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found with this slug."));
        return categoryMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findByUuid(UUID id) {
        return categoryMapper.toCategoryResponse(activeCategoryOr404(id, "Category not found with this id."));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID uuid, UpdateCategoryRequest req) {
        Category category = activeCategoryOr404(uuid, "Category not found");

        if (req.name() != null) {
            if (categoryRepository.existsByNameAndUuidNot(req.name(), uuid)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another category already uses this name.");
            }
            category.setName(req.name());
        }
        if (req.slug() != null) {
            if (categoryRepository.existsBySlugAndUuidNot(req.slug(), uuid)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another category already uses this slug.");
            }
            category.setSlug(req.slug());
        }
        if (req.iconFileId() != null) {
            category.setIconFile(resolveIcon(req.iconFileId()));
        }
        if (req.description() != null) {
            category.setDescription(req.description());
        }
        if (req.sortOrder() != null) {
            category.setSortOrder(req.sortOrder());
        }
        if (req.isActive() != null) {
            category.setIsActive(req.isActive());
        }
        if (req.parentUuid() != null) {
            Category parent = activeCategoryOr404(req.parentUuid(), "Parent category not found");
            assertNotOwnDescendant(category, parent);
            category.setParentCategory(parent);
            // Depth changed, so every node underneath shifts with it.
            applyLevels(category, levelUnder(parent), new HashSet<>());
        }

        return categoryMapper.toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse removeIcon(UUID uuid) {
        Category category = activeCategoryOr404(uuid, "Category not found");
        // Only the reference is dropped: the same file may back other categories,
        // and deleting the object is an explicit call on the file API.
        category.setIconFile(null);
        return categoryMapper.toCategoryResponse(categoryRepository.save(category));
    }

    /**
     * Soft-deletes the category and everything beneath it. Deleting only the
     * parent used to strand its children: still active, but hanging off a deleted
     * parent and therefore missing from the tree.
     */
    @Override
    @Transactional
    public void softDelete(String slug) {
        Category category = categoryRepository.findBySlugAndIsDeletedFalse(slug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found with this slug"));
        markDeleted(category);
        categoryRepository.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findChildByUuid(UUID uuid) {
        activeCategoryOr404(uuid, "Category not found with this UUID.");
        return categoryRepository
                .findAllByParentCategory_UuidAndIsDeletedFalse(uuid, DISPLAY_ORDER)
                .stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> categories = categoryRepository.findAllByIsDeletedFalse(DISPLAY_ORDER);

        Map<UUID, CategoryTreeResponse> byUuid = new LinkedHashMap<>();
        for (Category c : categories) {
            byUuid.put(c.getUuid(), categoryMapper.toCategoryTreeResponse(c));
        }

        List<CategoryTreeResponse> roots = new ArrayList<>();
        for (Category c : categories) {
            CategoryTreeResponse dto = byUuid.get(c.getUuid());
            Category parent = c.getParentCategory();
            CategoryTreeResponse parentDto = parent == null ? null : byUuid.get(parent.getUuid());

            if (parentDto != null) {
                parentDto.children().add(dto);
            } else {
                // No parent, or a parent that is soft-deleted: surface it as a root
                // rather than dropping the whole branch from the response.
                roots.add(dto);
            }
        }
        return roots;
    }

    private Category activeCategoryOr404(UUID uuid, String message) {
        return categoryRepository.findByUuidAndIsDeletedFalse(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    }

    private FileUpload resolveIcon(UUID iconFileId) {
        if (iconFileId == null) {
            return null;
        }
        FileUpload file = fileUploadRepository.findById(iconFileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Icon file not found"));

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "A category icon must be an image file.");
        }
        return file;
    }

    private int levelUnder(Category parent) {
        return parent == null ? 1 : parent.getLevel() + 1;
    }

    /**
     * Walks up from the proposed parent. Reaching the category itself means the
     * move would close a loop — including the trivial case of self-parenting.
     */
    private void assertNotOwnDescendant(Category category, Category newParent) {
        for (Category ancestor = newParent; ancestor != null; ancestor = ancestor.getParentCategory()) {
            if (ancestor.getUuid().equals(category.getUuid())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A category cannot be its own parent or a descendant of itself.");
            }
        }
    }

    /**
     * @param visited guards against a cycle in pre-existing data, which nothing
     *                stopped being created before {@link #assertNotOwnDescendant}
     *                existed and which would otherwise recurse forever.
     */
    private void applyLevels(Category category, int level, Set<UUID> visited) {
        if (!visited.add(category.getUuid())) {
            return;
        }
        category.setLevel(level);
        if (category.getChildCategories() != null) {
            for (Category child : category.getChildCategories()) {
                applyLevels(child, level + 1, visited);
            }
        }
    }

    private void markDeleted(Category category) {
        category.setIsDeleted(true);
        if (category.getChildCategories() != null) {
            for (Category child : category.getChildCategories()) {
                if (!Boolean.TRUE.equals(child.getIsDeleted())) {
                    markDeleted(child);
                }
            }
        }
    }
}
