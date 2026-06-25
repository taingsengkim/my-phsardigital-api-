package co.istad.sengkim.phsardigital.features.categories;

import co.istad.sengkim.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.sengkim.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.sengkim.phsardigital.features.categories.dto.CategoryTreeResponse;
import co.istad.sengkim.phsardigital.features.categories.dto.UpdateCategoryRequest;
import co.istad.sengkim.phsardigital.features.file.FileUpload;
import co.istad.sengkim.phsardigital.features.file.FileUploadRepository;
import co.istad.sengkim.phsardigital.features.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.MalformedParameterizedTypeException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService{
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final FileUploadService fileUploadService;
    private final FileUploadRepository fileUploadRepository;

    @Override
    public Page<CategoryResponse> findAll(int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber,pageSize);
        return categoryRepository.findAllByIsDeletedFalse(pageable).map(this::toResponse);
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        if (categoryRepository.existsByName((categoryRequest.name()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category with this name already exists.");
        }
        if (categoryRepository.existsBySlug((categoryRequest.slug()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category with this slug already exists.");
        }
        Category category = categoryMapper.toCategory(categoryRequest);
        if (categoryRequest.parentUuid() != null) {
            Category parent = categoryRepository.findById(categoryRequest.parentUuid())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));
            category.setParentCategory(parent);
        }
        category.setIsDeleted(false);
        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Override
    public CategoryResponse findBySlug(String categorySlug) {
        Category category = categoryRepository.findBySlug(categorySlug)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found with this slug."));
        return toResponse(category);
    }

    @Override
    public CategoryResponse findByUuid(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found with this id."));
        return toResponse(category);
    }

    @Override
    public CategoryResponse updateCategory(UUID uuid, UpdateCategoryRequest req) {
        Category category = categoryRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (req.name() != null) {
            if (categoryRepository.existsByNameAndUuidNot(req.name(), uuid)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            category.setName(req.name());
        }
        if (req.slug() != null) {
            if (categoryRepository.existsBySlugAndUuidNot(req.slug(), uuid)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            category.setSlug(req.slug());
        }
        if (req.iconFileId() != null) {
            FileUpload file = fileUploadRepository.findById(req.iconFileId())
                    .orElseThrow(() ->
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
            category.setIconFile(file);
        }
        if (req.description() != null) {
            category.setDescription(req.description());
        }
        if (req.level() != null) {
            category.setLevel(req.level());
        }
        if (req.isActive() != null) {
            category.setIsActive(req.isActive());
        }
        if (req.parentUuid() != null) {
            Category parent = categoryRepository.findById(req.parentUuid())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));
            category.setParentCategory(parent);
        }
        return toResponse(categoryRepository.save(category));
    }

    @Override
    public void softDeleteCategory(String slug) {
        Category category = categoryRepository.findBySlug(slug).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found with this slug"));
        category.setIsDeleted(true);
        categoryRepository.save(category);
    }

    @Override
    public List<CategoryResponse> findChildByUuid(UUID uuid) {
        Category parent = categoryRepository.findById(uuid).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found with this UUID."));
        return parent.getChildCategories()
                .stream().map(categoryMapper::toCategoryResponse).toList();
    }

    @Override
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> categories = categoryRepository.findAll();
        Map<UUID,CategoryTreeResponse> categoryTreeResponseMap = new HashMap<>();
        for (Category c : categories){
            CategoryTreeResponse dto = categoryMapper.toCategoryTreeResponse(c);
            categoryTreeResponseMap.put(c.getUuid(),dto);
            //O(1) later
        }
        List<CategoryTreeResponse> roots = new ArrayList<>();
        for (Category c : categories){
            CategoryTreeResponse dto = categoryTreeResponseMap.get(c.getUuid());
            Category parent = c.getParentCategory();

            if (parent == null) {
                roots.add(dto);
            } else {
                CategoryTreeResponse parentDto = categoryTreeResponseMap.get(parent.getUuid());
                if (parentDto != null) {
                    parentDto.children().add(dto);
                }
            }
        }
        return roots;
    }


    private CategoryResponse toResponse(Category category) {

        String iconUrl = null;

        if (category.getIconFile() != null) {
            iconUrl = fileUploadService.getPreviewUrl(
                    category.getIconFile().getObjectName()
            );
        }

        return new CategoryResponse(
                category.getUuid(),
                category.getName(),
                category.getSlug(),
                iconUrl,
                category.getDescription(),
                category.getLevel(),
                category.getSortOrder(),
                category.getIsActive(),
                category.getParentCategory() != null
                        ? category.getParentCategory().getUuid()
                        : null
        );
    }

}
