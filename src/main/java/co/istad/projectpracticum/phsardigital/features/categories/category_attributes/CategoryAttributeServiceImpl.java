package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryAttributeServiceImpl implements CategoryAttributeService {

    /** The heading attributes that named no group are printed under. */
    static final String UNGROUPED = "Other";

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final CategoryAttributeResolver categoryAttributeResolver;
    private final CategoryAttributeMapper categoryAttributeMapper;

    @Override
    @Transactional(readOnly = true)
    public CategoryAttributeSchemaResponse getSchema(UUID categoryUuid, boolean includeInherited) {
        return schemaFor(activeCategoryOr404(categoryUuid, "Category not found with this id."),
                includeInherited);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryAttributeSchemaResponse getSchemaBySlug(String categorySlug, boolean includeInherited) {
        Category category = categoryRepository.findBySlugAndIsDeletedFalse(categorySlug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found with this slug."));
        return schemaFor(category, includeInherited);
    }

    @Override
    @Transactional
    public List<CategoryAttributeResponse> create(UUID categoryUuid,
                                                  List<CategoryAttributeRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No attributes given.");
        }
        Category category = activeCategoryOr404(categoryUuid, "Category not found with this id.");

        // Checked against the batch as well as the table: two attributes sharing a code
        // inside one request would otherwise both pass and collide on insert.
        Set<String> codes = new HashSet<>();
        List<CategoryAttribute> attributes = new ArrayList<>();

        for (CategoryAttributeRequest request : requests) {
            String code = request.code().trim().toLowerCase(Locale.ROOT);
            if (!codes.add(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Duplicate attribute code in this request: " + code);
            }
            if (categoryAttributeRepository.existsByCategory_UuidAndCodeAndIsDeletedFalse(categoryUuid, code)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This category already defines an attribute with code '" + code + "'.");
            }

            CategoryAttribute attribute = new CategoryAttribute();
            attribute.setCategory(category);
            attribute.setCode(code);
            attribute.setLabel(request.label().trim());
            attribute.setGroupName(trimToNull(request.group()));
            attribute.setDataType(request.dataType() == null ? AttributeDataType.TEXT : request.dataType());
            attribute.setUnit(trimToNull(request.unit()));
            attribute.setRequired(Boolean.TRUE.equals(request.required()));
            attribute.setFilterable(Boolean.TRUE.equals(request.filterable()));
            attribute.setMinValue(request.minValue());
            attribute.setMaxValue(request.maxValue());
            attribute.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
            attribute.setGroupSortOrder(request.groupSortOrder() == null ? 0 : request.groupSortOrder());
            attribute.setIsDeleted(false);

            replaceOptions(attribute, request.options());
            requireCoherent(attribute);
            attributes.add(attribute);
        }

        return categoryAttributeRepository.saveAll(attributes).stream()
                .map(categoryAttributeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryAttributeResponse update(UUID categoryUuid, UUID attributeUuid,
                                            UpdateCategoryAttributeRequest request) {
        activeCategoryOr404(categoryUuid, "Category not found with this id.");
        CategoryAttribute attribute = categoryAttributeRepository.findByUuidAndIsDeletedFalse(attributeUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Attribute not found."));
        requireOwnedBy(attribute, categoryUuid);

        if (request.code() != null) {
            String code = request.code().trim().toLowerCase(Locale.ROOT);
            if (categoryAttributeRepository
                    .existsByCategory_UuidAndCodeAndIsDeletedFalseAndUuidNot(categoryUuid, code, attributeUuid)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This category already defines an attribute with code '" + code + "'.");
            }
            attribute.setCode(code);
        }
        if (request.label() != null) {
            attribute.setLabel(request.label().trim());
        }
        if (request.group() != null) {
            // Blank is how a group is cleared, since an empty heading is not a heading.
            attribute.setGroupName(trimToNull(request.group()));
        }
        if (request.dataType() != null) {
            attribute.setDataType(request.dataType());
        }
        if (request.unit() != null) {
            attribute.setUnit(trimToNull(request.unit()));
        }
        if (request.required() != null) {
            attribute.setRequired(request.required());
        }
        if (request.filterable() != null) {
            attribute.setFilterable(request.filterable());
        }
        if (request.minValue() != null) {
            attribute.setMinValue(request.minValue());
        }
        if (request.maxValue() != null) {
            attribute.setMaxValue(request.maxValue());
        }
        if (request.sortOrder() != null) {
            attribute.setSortOrder(request.sortOrder());
        }
        if (request.groupSortOrder() != null) {
            attribute.setGroupSortOrder(request.groupSortOrder());
        }
        if (request.options() != null) {
            replaceOptions(attribute, request.options());
        }

        // Checked after everything is applied, so switching a TEXT attribute to SELECT and
        // supplying its options in the same call is allowed, and switching without them
        // is not.
        requireCoherent(attribute);

        return categoryAttributeMapper.toResponse(categoryAttributeRepository.save(attribute));
    }

    @Override
    @Transactional
    public void delete(UUID categoryUuid, UUID attributeUuid) {
        activeCategoryOr404(categoryUuid, "Category not found with this id.");
        CategoryAttribute attribute = categoryAttributeRepository.findByUuidAndIsDeletedFalse(attributeUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Attribute not found."));
        requireOwnedBy(attribute, categoryUuid);

        attribute.setIsDeleted(true);
        categoryAttributeRepository.save(attribute);
    }

    private CategoryAttributeSchemaResponse schemaFor(Category category, boolean includeInherited) {
        List<CategoryAttribute> attributes = includeInherited
                ? categoryAttributeResolver.effectiveFor(category)
                : categoryAttributeResolver.declaredBy(category);

        List<CategoryAttributeResponse> responses = attributes.stream()
                .map(attribute -> {
                    CategoryAttributeResponse response = categoryAttributeMapper.toResponse(attribute);
                    return attribute.getCategory().getUuid().equals(category.getUuid())
                            ? response
                            : response.asInherited();
                })
                .toList();

        // Insertion-ordered: the resolver already sorted the attributes, so the groups
        // come out in the order their first member appears.
        Map<String, List<CategoryAttributeResponse>> grouped = new LinkedHashMap<>();
        for (CategoryAttributeResponse response : responses) {
            String group = response.group() == null || response.group().isBlank()
                    ? UNGROUPED
                    : response.group();
            grouped.computeIfAbsent(group, key -> new ArrayList<>()).add(response);
        }

        List<CategoryAttributeGroupResponse> groups = grouped.entrySet().stream()
                .map(entry -> new CategoryAttributeGroupResponse(entry.getKey(), entry.getValue()))
                .toList();

        return new CategoryAttributeSchemaResponse(
                category.getUuid(), category.getSlug(), category.getName(), groups, responses);
    }

    /**
     * Replaces the option list wholesale.
     *
     * <p>Options that survive the replacement are reused rather than deleted and
     * re-created: within one flush Hibernate inserts before it deletes, so adding XL to
     * [XS, S, M, L] by rewriting all five would insert a second XS against the
     * {@code (attribute_uuid, value)} constraint while the first is still on its way out.
     *
     * <p>{@code clear()} on the tracked collection rather than a new list, because orphan
     * removal only sees what happens to the collection Hibernate is holding.
     */
    private void replaceOptions(CategoryAttribute attribute, List<CategoryAttributeOptionRequest> requests) {
        List<CategoryAttributeOption> options = attribute.getOptions();
        Map<String, CategoryAttributeOption> existing = new LinkedHashMap<>();
        for (CategoryAttributeOption option : options) {
            existing.putIfAbsent(option.getValue().toLowerCase(Locale.ROOT), option);
        }

        List<CategoryAttributeOption> replacement = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int position = 0;
        for (CategoryAttributeOptionRequest request : requests == null ? List.<CategoryAttributeOptionRequest>of() : requests) {
            String value = request.value().trim();
            String key = value.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Duplicate option '" + value + "' on attribute '" + attribute.getCode() + "'.");
            }
            CategoryAttributeOption option = existing.get(key);
            if (option == null) {
                option = new CategoryAttributeOption();
                option.setAttribute(attribute);
            }
            option.setValue(value);
            option.setLabel(trimToNull(request.label()));
            option.setSortOrder(request.sortOrder() == null ? position : request.sortOrder());
            replacement.add(option);
            position++;
        }

        options.clear();
        options.addAll(replacement);
    }

    /**
     * The two ways a definition can be self-contradictory: a dropdown with nothing to
     * choose from, and a range that excludes every number.
     */
    private void requireCoherent(CategoryAttribute attribute) {
        if (attribute.isChoice() && attribute.getOptions().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A " + attribute.getDataType() + " attribute needs at least one option: "
                            + attribute.getCode());
        }
        if (!attribute.isChoice() && !attribute.getOptions().isEmpty()) {
            // Reachable by changing a SELECT to a TEXT without saying what becomes of its
            // options, so the message names the way out rather than just the rule.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only SELECT and MULTI_SELECT attributes take options: " + attribute.getCode()
                            + ". Send \"options\": [] to drop them.");
        }
        if (attribute.getDataType() != AttributeDataType.NUMBER
                && (attribute.getMinValue() != null || attribute.getMaxValue() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only NUMBER attributes take a minimum or maximum: " + attribute.getCode());
        }
        if (attribute.getMinValue() != null && attribute.getMaxValue() != null
                && attribute.getMinValue() > attribute.getMaxValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum must not exceed maximum on attribute: " + attribute.getCode());
        }
    }

    /**
     * An inherited attribute shows up on every category beneath its owner. Editing it
     * through one of those would silently change it for all the others, so the owner is
     * the only category it may be addressed through.
     */
    private void requireOwnedBy(CategoryAttribute attribute, UUID categoryUuid) {
        if (!attribute.getCategory().getUuid().equals(categoryUuid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This attribute is inherited from '" + attribute.getCategory().getSlug()
                            + "'. Edit it on that category.");
        }
    }

    private Category activeCategoryOr404(UUID uuid, String message) {
        return categoryRepository.findByUuidAndIsDeletedFalse(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
