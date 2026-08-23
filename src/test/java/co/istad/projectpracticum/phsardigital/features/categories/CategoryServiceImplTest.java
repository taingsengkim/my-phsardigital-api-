package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.UpdateCategoryRequest;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingSchemaImpactValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private FileUploadRepository fileUploadRepository;
    @Mock
    private ListingSchemaImpactValidator listingSchemaImpactValidator;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(
                categoryRepository,
                categoryMapper,
                fileUploadRepository,
                new CategoryAvailability(),
                listingSchemaImpactValidator);
    }

    @Test
    void refusesAnActiveCategoryBelowAnInactiveAncestor() {
        Category inactiveRoot = category("root", false, false);
        Category activeParent = category("parent", true, false);
        link(inactiveRoot, activeParent);
        when(categoryRepository.findMutableByUuidForUpdate(activeParent.getUuid()))
                .thenReturn(Optional.of(activeParent));

        CategoryRequest request = new CategoryRequest(
                "Phones", "phones", null, null, 0, true, activeParent.getUuid());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active, non-deleted hierarchy");
    }

    @Test
    void refusesActivationBelowAnInactiveParent() {
        Category parent = category("parent", false, false);
        Category child = category("child", false, false);
        link(parent, child);
        when(categoryRepository.findMutableByUuidForUpdate(child.getUuid()))
                .thenReturn(Optional.of(child));

        UpdateCategoryRequest request = update(null, true, null, null);

        assertThatThrownBy(() -> service.updateCategory(child.getUuid(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active, non-deleted hierarchy");
    }

    @Test
    void deactivationCascadesThroughAnAlreadyInactiveChild() {
        Category root = category("root", true, false);
        Category inactiveChild = category("child", false, false);
        Category activeGrandchild = category("grandchild", true, false);
        link(root, inactiveChild);
        link(inactiveChild, activeGrandchild);
        when(categoryRepository.findMutableByUuidForUpdate(root.getUuid()))
                .thenReturn(Optional.of(root));
        when(categoryRepository.save(root)).thenReturn(root);

        service.updateCategory(root.getUuid(), update(null, false, null, null));

        assertThat(List.of(root, inactiveChild, activeGrandchild))
                .allMatch(category -> !Boolean.TRUE.equals(category.getIsActive()));
    }

    @Test
    void softDeleteTraversesDeletedBranchesAndSurvivesACycle() {
        Category root = category("root", true, false);
        Category deletedChild = category("child", false, true);
        Category activeGrandchild = category("grandchild", true, false);
        link(root, deletedChild);
        link(deletedChild, activeGrandchild);
        // Corrupt collection data must not make deletion recurse forever.
        activeGrandchild.getChildCategories().add(root);
        when(categoryRepository.findMutableBySlugForUpdate("root"))
                .thenReturn(Optional.of(root));

        service.softDelete(" ROOT ");

        assertThat(List.of(root, deletedChild, activeGrandchild))
                .allMatch(category -> Boolean.TRUE.equals(category.getIsDeleted())
                        && !Boolean.TRUE.equals(category.getIsActive()));
    }

    @Test
    void explicitMoveToRootRelevelsTheWholeSubtree() {
        Category oldParent = category("old-parent", true, false);
        oldParent.setLevel(1);
        Category category = category("phones", true, false);
        category.setLevel(2);
        Category child = category("smartphones", true, false);
        child.setLevel(3);
        link(oldParent, category);
        link(category, child);
        when(categoryRepository.findMutableByUuidForUpdate(category.getUuid()))
                .thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        service.updateCategory(category.getUuid(), update(null, null, null, true));

        assertThat(category.getParentCategory()).isNull();
        assertThat(category.getLevel()).isEqualTo(1);
        assertThat(child.getLevel()).isEqualTo(2);
        verify(listingSchemaImpactValidator).revalidatePublishedListings(category);
    }

    @Test
    void activationRevalidatesListingsBeforeTheCategoryBecomesPublic() {
        Category category = category("phones", false, false);
        when(categoryRepository.findMutableByUuidForUpdate(category.getUuid()))
                .thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        service.updateCategory(category.getUuid(), update(null, true, null, null));

        verify(listingSchemaImpactValidator).revalidatePublishedListings(category);
    }

    @Test
    void rejectsParentUuidTogetherWithMoveToRoot() {
        Category category = category("phones", false, false);
        when(categoryRepository.findMutableByUuidForUpdate(category.getUuid()))
                .thenReturn(Optional.of(category));

        UpdateCategoryRequest request = update(null, null, UUID.randomUUID(), true);

        assertThatThrownBy(() -> service.updateCategory(category.getUuid(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not both");
    }

    @Test
    void rejectsAMoveWhenTheSubtreeWouldCrossTheDepthLimit() {
        Category proposedParent = chain(CategoryAvailability.MAX_HIERARCHY_DEPTH - 1);
        Category moving = category("moving", true, false);
        Category child = category("moving-child", true, false);
        link(moving, child);
        when(categoryRepository.findMutableByUuidForUpdate(moving.getUuid()))
                .thenReturn(Optional.of(moving));
        when(categoryRepository.findMutableByUuidForUpdate(proposedParent.getUuid()))
                .thenReturn(Optional.of(proposedParent));

        UpdateCategoryRequest request = update(null, null, proposedParent.getUuid(), null);

        assertThatThrownBy(() -> service.updateCategory(moving.getUuid(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must not exceed");
    }

    @Test
    void publicPaginationDropsAChildWithInactiveAncestry() {
        Category visible = category("visible", true, false);
        Category inactiveRoot = category("inactive", false, false);
        Category legacyActiveChild = category("legacy-child", true, false);
        link(inactiveRoot, legacyActiveChild);
        when(categoryRepository.findAllByIsDeletedFalseAndIsActiveTrue(any(Sort.class)))
                .thenReturn(List.of(visible, legacyActiveChild));
        CategoryResponse response = responseOf(visible);
        when(categoryMapper.toCategoryResponse(visible)).thenReturn(response);

        var page = service.findAll(0, 25);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).containsExactly(response);
    }

    @Test
    void adminPaginationIncludesInactiveButNonDeletedCategories() {
        Category inactive = category("draft-category", false, false);
        CategoryResponse response = responseOf(inactive);
        when(categoryRepository.findAllByIsDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(inactive), PageRequest.of(0, 25), 1));
        when(categoryMapper.toCategoryResponse(inactive)).thenReturn(response);

        var page = service.findAllForAdmin(0, 25);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).containsExactly(response);
        assertThat(page.getContent().getFirst().isActive()).isFalse();
    }

    @Test
    void adminLookupCanReadAnInactiveNonDeletedCategory() {
        Category inactive = category("draft-category", false, false);
        CategoryResponse response = responseOf(inactive);
        when(categoryRepository.findByUuidAndIsDeletedFalse(inactive.getUuid()))
                .thenReturn(Optional.of(inactive));
        when(categoryMapper.toCategoryResponse(inactive)).thenReturn(response);

        assertThat(service.findByUuidForAdmin(inactive.getUuid())).isEqualTo(response);
    }

    @Test
    void createNormalisesNamesAndSlugsAndChecksTheSiblingScope() {
        Category mapped = new Category();
        when(categoryMapper.toCategory(any())).thenReturn(mapped);
        when(categoryRepository.save(mapped)).thenReturn(mapped);

        service.create(new CategoryRequest(
                "  Mobile   Phones  ", " PHONES ", null, null, null, false, null));

        verify(categoryRepository)
                .existsByParentCategoryIsNullAndNameIgnoreCaseAndIsDeletedFalse("Mobile Phones");
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Mobile Phones");
        assertThat(captor.getValue().getSlug()).isEqualTo("phones");
        assertThat(captor.getValue().getSortOrder()).isZero();
    }

    private static UpdateCategoryRequest update(String name, Boolean active,
                                                UUID parentUuid, Boolean moveToRoot) {
        return new UpdateCategoryRequest(
                name, null, null, null, null, active, parentUuid, moveToRoot);
    }

    private static Category chain(int depth) {
        Category parent = null;
        for (int i = 1; i <= depth; i++) {
            Category next = category("level-" + i, true, false);
            next.setLevel(i);
            if (parent != null) {
                link(parent, next);
            }
            parent = next;
        }
        return parent;
    }

    private static void link(Category parent, Category child) {
        child.setParentCategory(parent);
        parent.getChildCategories().add(child);
    }

    private static Category category(String slug, boolean active, boolean deleted) {
        Category category = new Category();
        category.setUuid(UUID.randomUUID());
        category.setName(slug);
        category.setSlug(slug);
        category.setLevel(1);
        category.setIsActive(active);
        category.setIsDeleted(deleted);
        return category;
    }

    private static CategoryResponse responseOf(Category category) {
        return new CategoryResponse(
                category.getUuid(), category.getName(), category.getSlug(), null,
                null, category.getLevel(), category.getSortOrder(), category.getIsActive(), null);
    }
}
