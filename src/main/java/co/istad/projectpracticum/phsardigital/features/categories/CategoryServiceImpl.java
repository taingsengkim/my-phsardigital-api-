package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryTreeResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.UpdateCategoryRequest;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingSchemaImpactValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    private final CategoryAvailability categoryAvailability;
    private final ListingSchemaImpactValidator listingSchemaImpactValidator;

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAll(int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, DISPLAY_ORDER);
        List<Category> visible = categoryRepository
                .findAllByIsDeletedFalseAndIsActiveTrue(DISPLAY_ORDER)
                .stream()
                .filter(categoryAvailability::isEffectivelyActive)
                .toList();

        int start = (int) Math.min(pageable.getOffset(), visible.size());
        int end = Math.min(start + pageable.getPageSize(), visible.size());
        List<CategoryResponse> content = visible.subList(start, end).stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
        return new PageImpl<>(content, pageable, visible.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAllForAdmin(int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, DISPLAY_ORDER);
        return categoryRepository.findAllByIsDeletedFalse(pageable)
                .map(categoryMapper::toCategoryResponse);
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest categoryRequest) {
        String name = normaliseName(categoryRequest.name());
        String slug = normaliseSlug(categoryRequest.slug());
        if (categoryRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category with this slug already exists.");
        }

        Category parent = categoryRequest.parentUuid() == null
                ? null
                : mutableCategoryOr404(categoryRequest.parentUuid(), "Parent category not found");
        requireUniqueSiblingName(name, parent, null);
        requireActiveAncestryWhenActive(categoryRequest.isActive(), parent);

        int level = levelUnder(parent);
        if (level > CategoryAvailability.MAX_HIERARCHY_DEPTH) {
            throw hierarchyTooDeep();
        }

        Category category = categoryMapper.toCategory(categoryRequest);
        category.setName(name);
        category.setSlug(slug);
        category.setParentCategory(parent);
        category.setLevel(level);

        // The icon was silently dropped here before, so a category could only get
        // one through a follow-up PATCH.
        category.setIconFile(resolveIcon(categoryRequest.iconFileId()));
        category.setSortOrder(categoryRequest.sortOrder() == null ? 0 : categoryRequest.sortOrder());
        category.setIsActive(Boolean.TRUE.equals(categoryRequest.isActive()));
        category.setIsDeleted(false);

        return categoryMapper.toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findBySlug(String categorySlug) {
        Category category = categoryRepository.findBySlugAndIsDeletedFalse(normaliseSlug(categorySlug))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found with this slug."));
        requirePubliclyAvailable(category, "Category not found with this slug.");
        return categoryMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findByUuid(UUID id) {
        Category category = categoryRepository.findByUuidAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found with this id."));
        requirePubliclyAvailable(category, "Category not found with this id.");
        return categoryMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findByUuidForAdmin(UUID id) {
        Category category = categoryRepository.findByUuidAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found with this id."));
        return categoryMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID uuid, UpdateCategoryRequest req) {
        Category category = mutableCategoryOr404(uuid, "Category not found");
        boolean wasPubliclyAvailable = categoryAvailability.isEffectivelyActive(category);

        boolean moveToRoot = Boolean.TRUE.equals(req.moveToRoot());
        if (moveToRoot && req.parentUuid() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose either parentUuid or moveToRoot, not both.");
        }

        Category newParent = category.getParentCategory();
        if (moveToRoot) {
            newParent = null;
        } else if (req.parentUuid() != null) {
            newParent = mutableCategoryOr404(req.parentUuid(), "Parent category not found");
            assertNotOwnDescendant(category, newParent);
        }

        if (req.name() != null && newParent != null && req.parentUuid() == null) {
            // Sibling-name uniqueness is a check-then-write invariant. Lock their
            // shared parent so two sibling renames cannot both pass the check.
            newParent = mutableCategoryOr404(newParent.getUuid(), "Parent category not found");
        }

        String finalName = req.name() == null ? category.getName() : normaliseName(req.name());
        String finalSlug = req.slug() == null ? category.getSlug() : normaliseSlug(req.slug());
        boolean parentChanged = !sameCategory(category.getParentCategory(), newParent);

        if (req.name() != null || parentChanged) {
            requireUniqueSiblingName(finalName, newParent, uuid);
        }
        if (req.slug() != null) {
            if (categoryRepository.existsBySlugAndUuidNot(finalSlug, uuid)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another category already uses this slug.");
            }
        }

        boolean finalActive = req.isActive() == null
                ? Boolean.TRUE.equals(category.getIsActive())
                : Boolean.TRUE.equals(req.isActive());
        requireActiveAncestryWhenActive(finalActive, newParent);

        int newLevel = levelUnder(newParent);
        int subtreeHeight = subtreeHeight(category);
        if (newLevel + subtreeHeight - 1 > CategoryAvailability.MAX_HIERARCHY_DEPTH) {
            throw hierarchyTooDeep();
        }

        category.setName(finalName);
        category.setSlug(finalSlug);
        if (parentChanged) {
            category.setParentCategory(newParent);
            applyLevels(category, newLevel);
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
        if (finalActive) {
            if (req.isActive() != null) {
                category.setIsActive(true);
            }
        } else {
            // Also repairs legacy data where an inactive category still has active
            // descendants, even when this PATCH changed a different field.
            deactivateSubtree(category);
        }

        Category saved = categoryRepository.save(category);
        if (parentChanged
                || (!wasPubliclyAvailable && categoryAvailability.isEffectivelyActive(category))) {
            // Reparenting changes inherited attributes, and activation can expose stale
            // drafts written while a branch was hidden. Refuse the hierarchy change if
            // any already-published listing would no longer satisfy its effective schema.
            listingSchemaImpactValidator.revalidatePublishedListings(category);
        }
        return categoryMapper.toCategoryResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse removeIcon(UUID uuid) {
        Category category = mutableCategoryOr404(uuid, "Category not found");
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
        Category category = categoryRepository.findMutableBySlugForUpdate(normaliseSlug(slug))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found with this slug"));
        markDeletedAndInactive(category);
        categoryRepository.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findChildByUuid(UUID uuid) {
        Category parent = categoryRepository.findByUuidAndIsDeletedFalse(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found with this UUID."));
        requirePubliclyAvailable(parent, "Category not found with this UUID.");
        return categoryRepository
                .findAllByParentCategory_UuidAndIsDeletedFalseAndIsActiveTrue(uuid, DISPLAY_ORDER)
                .stream()
                .filter(categoryAvailability::isEffectivelyActive)
                .map(categoryMapper::toCategoryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> categories = categoryRepository
                .findAllByIsDeletedFalseAndIsActiveTrue(DISPLAY_ORDER)
                .stream()
                .filter(categoryAvailability::isEffectivelyActive)
                .toList();

        Map<UUID, CategoryTreeResponse> byUuid = new LinkedHashMap<>();
        for (Category c : categories) {
            byUuid.put(c.getUuid(), categoryMapper.toCategoryTreeResponse(c));
        }

        List<CategoryTreeResponse> roots = new ArrayList<>();
        for (Category c : categories) {
            CategoryTreeResponse dto = byUuid.get(c.getUuid());
            Category parent = c.getParentCategory();
            CategoryTreeResponse parentDto = parent == null ? null : byUuid.get(parent.getUuid());

            if (parent == null) {
                roots.add(dto);
            } else if (parentDto != null) {
                parentDto.children().add(dto);
            }
        }
        return roots;
    }

    /** Mutation lookup deliberately includes inactive categories; only deleted rows are gone. */
    private Category mutableCategoryOr404(UUID uuid, String message) {
        return categoryRepository.findMutableByUuidForUpdate(uuid)
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
        try {
            return categoryAvailability.depthOf(parent) + 1;
        } catch (CategoryAvailability.InvalidHierarchyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    /**
     * Walks up from the proposed parent. Reaching the category itself means the
     * move would close a loop — including the trivial case of self-parenting.
     */
    private void assertNotOwnDescendant(Category category, Category newParent) {
        Set<UUID> visited = new HashSet<>();
        int depth = 0;
        for (Category ancestor = newParent; ancestor != null; ancestor = ancestor.getParentCategory()) {
            depth++;
            if (depth > CategoryAvailability.MAX_HIERARCHY_DEPTH) {
                throw hierarchyTooDeep();
            }
            if (!visited.add(ancestor.getUuid())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The existing category hierarchy contains a cycle.");
            }
            if (ancestor.getUuid().equals(category.getUuid())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A category cannot be its own parent or a descendant of itself.");
            }
        }
    }

    /** Relevels a whole moved branch, rejecting corrupt cycles rather than hiding them. */
    private void applyLevels(Category category, int level) {
        Deque<CategoryAtDepth> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        pending.add(new CategoryAtDepth(category, level));

        while (!pending.isEmpty()) {
            CategoryAtDepth next = pending.removeFirst();
            if (!visited.add(next.category().getUuid())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The category subtree contains a cycle.");
            }
            if (next.depth() > CategoryAvailability.MAX_HIERARCHY_DEPTH) {
                throw hierarchyTooDeep();
            }
            next.category().setLevel(next.depth());
            if (next.category().getChildCategories() != null) {
                for (Category child : next.category().getChildCategories()) {
                    pending.addLast(new CategoryAtDepth(child, next.depth() + 1));
                }
            }
        }
    }

    /** The height of a moved branch, with its root counting as one. */
    private int subtreeHeight(Category category) {
        Deque<CategoryAtDepth> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        pending.add(new CategoryAtDepth(category, 1));
        int height = 0;

        while (!pending.isEmpty()) {
            CategoryAtDepth next = pending.removeFirst();
            if (!visited.add(next.category().getUuid())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The category subtree contains a cycle.");
            }
            height = Math.max(height, next.depth());
            if (height > CategoryAvailability.MAX_HIERARCHY_DEPTH) {
                throw hierarchyTooDeep();
            }
            if (next.category().getChildCategories() != null) {
                for (Category child : next.category().getChildCategories()) {
                    pending.addLast(new CategoryAtDepth(child, next.depth() + 1));
                }
            }
        }
        return height;
    }

    /** Deactivation is inherited downwards; activation remains an explicit child choice. */
    private void deactivateSubtree(Category category) {
        visitSubtree(category, current -> current.setIsActive(false));
    }

    /** Soft deletion also deactivates every descendant, including already-deleted branches. */
    private void markDeletedAndInactive(Category category) {
        visitSubtree(category, current -> {
            current.setIsDeleted(true);
            current.setIsActive(false);
        });
    }

    /** Iterative and visited: malformed cycles cannot overflow the stack or hang deletion. */
    private void visitSubtree(Category category, java.util.function.Consumer<Category> action) {
        Deque<Category> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        pending.add(category);

        while (!pending.isEmpty()) {
            Category current = pending.removeFirst();
            if (!visited.add(current.getUuid())) {
                continue;
            }
            action.accept(current);
            if (current.getChildCategories() != null) {
                pending.addAll(current.getChildCategories());
            }
        }
    }

    private void requireActiveAncestryWhenActive(Boolean active, Category parent) {
        requireActiveAncestryWhenActive(Boolean.TRUE.equals(active), parent);
    }

    private void requireActiveAncestryWhenActive(boolean active, Category parent) {
        if (active && parent != null && !categoryAvailability.isEffectivelyActive(parent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An active category must be placed beneath an active, non-deleted hierarchy.");
        }
    }

    private void requirePubliclyAvailable(Category category, String message) {
        if (!categoryAvailability.isEffectivelyActive(category)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
    }

    private void requireUniqueSiblingName(String name, Category parent, UUID excludedUuid) {
        boolean exists;
        if (parent == null) {
            exists = excludedUuid == null
                    ? categoryRepository.existsByParentCategoryIsNullAndNameIgnoreCaseAndIsDeletedFalse(name)
                    : categoryRepository
                    .existsByParentCategoryIsNullAndNameIgnoreCaseAndIsDeletedFalseAndUuidNot(
                            name, excludedUuid);
        } else {
            exists = excludedUuid == null
                    ? categoryRepository
                    .existsByParentCategory_UuidAndNameIgnoreCaseAndIsDeletedFalse(
                            parent.getUuid(), name)
                    : categoryRepository
                    .existsByParentCategory_UuidAndNameIgnoreCaseAndIsDeletedFalseAndUuidNot(
                            parent.getUuid(), name, excludedUuid);
        }
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another category under this parent already uses the name '" + name + "'.");
        }
    }

    private static boolean sameCategory(Category first, Category second) {
        if (first == second) {
            return true;
        }
        return first != null && second != null && Objects.equals(first.getUuid(), second.getUuid());
    }

    private static String normaliseName(String value) {
        String normalised = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalised.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name is required.");
        }
        return normalised;
    }

    private static String normaliseSlug(String value) {
        String normalised = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category slug is required.");
        }
        return normalised;
    }

    private static ResponseStatusException hierarchyTooDeep() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Category hierarchy must not exceed "
                        + CategoryAvailability.MAX_HIERARCHY_DEPTH + " levels.");
    }

    private record CategoryAtDepth(Category category, int depth) {
    }
}
