package co.istad.projectpracticum.phsardigital.features.categories;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryAvailabilityTest {

    private final CategoryAvailability availability = new CategoryAvailability();

    @Test
    void requiresEveryAncestorToBeActiveAndNotDeleted() {
        Category root = category(true, false, null);
        Category child = category(true, false, root);

        assertThat(availability.isEffectivelyActive(child)).isTrue();

        root.setIsActive(false);
        assertThat(availability.isEffectivelyActive(child)).isFalse();

        root.setIsActive(true);
        root.setIsDeleted(true);
        assertThat(availability.isEffectivelyActive(child)).isFalse();
    }

    @Test
    void failsClosedOnAParentCycle() {
        Category first = category(true, false, null);
        Category second = category(true, false, first);
        first.setParentCategory(second);

        assertThat(availability.isEffectivelyActive(first)).isFalse();
        assertThatThrownBy(() -> availability.depthOf(first))
                .isInstanceOf(CategoryAvailability.InvalidHierarchyException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void acceptsTheSharedMaximumDepth() {
        Category deepest = chain(CategoryAvailability.MAX_HIERARCHY_DEPTH);

        assertThat(availability.depthOf(deepest))
                .isEqualTo(CategoryAvailability.MAX_HIERARCHY_DEPTH);
        assertThat(availability.isEffectivelyActive(deepest)).isTrue();
    }

    @Test
    void rejectsAnythingBeyondTheSharedMaximumDepth() {
        Category tooDeep = chain(CategoryAvailability.MAX_HIERARCHY_DEPTH + 1);

        assertThat(availability.isEffectivelyActive(tooDeep)).isFalse();
        assertThatThrownBy(() -> availability.depthOf(tooDeep))
                .isInstanceOf(CategoryAvailability.InvalidHierarchyException.class)
                .hasMessageContaining("maximum depth");
    }

    private static Category chain(int length) {
        Category parent = null;
        for (int i = 0; i < length; i++) {
            parent = category(true, false, parent);
        }
        return parent;
    }

    private static Category category(boolean active, boolean deleted, Category parent) {
        Category category = new Category();
        category.setUuid(UUID.randomUUID());
        category.setIsActive(active);
        category.setIsDeleted(deleted);
        category.setParentCategory(parent);
        return category;
    }
}
