package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How a category's schema is worked out: which categories it is drawn from, which
 * declaration wins when two disagree, and what order the result comes out in.
 */
@ExtendWith(MockitoExtension.class)
class CategoryAttributeResolverTest {

    @Mock
    private CategoryAttributeRepository categoryAttributeRepository;

    private CategoryAttributeResolver resolver;

    private Category electronics;
    private Category phones;

    @BeforeEach
    void setUp() {
        resolver = new CategoryAttributeResolver(categoryAttributeRepository);
        electronics = category("electronics", null);
        phones = category("phones", electronics);
    }

    @Test
    void asksForTheWholeBranchFromTheCategoryUpToTheRoot() {
        when(categoryAttributeRepository.findAllByCategory_UuidInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of());

        resolver.effectiveFor(phones);

        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.captor();
        verify(categoryAttributeRepository).findAllByCategory_UuidInAndIsDeletedFalse(captor.capture());
        assertThat(captor.getValue())
                .containsExactly(phones.getUuid(), electronics.getUuid());
    }

    /** An ancestor's attribute applies here too, which is the point of declaring it there. */
    @Test
    void inheritsWhatAnAncestorDeclares() {
        stub(attribute(electronics, "brand", "Brand", "General", 0, 0),
                attribute(phones, "screen_size", "Screen size", "Display", 1, 0));

        assertThat(resolver.effectiveFor(phones))
                .extracting(CategoryAttribute::getCode)
                .containsExactly("brand", "screen_size");
    }

    /**
     * Narrowing an inherited attribute is done by re-declaring it, so the nearer
     * declaration has to be the one that survives.
     */
    @Test
    void theNearestDeclarationOfACodeWins() {
        CategoryAttribute inherited = attribute(electronics, "brand", "Brand", "General", 0, 0);
        CategoryAttribute overridden = attribute(phones, "brand", "Phone brand", "General", 0, 0);
        // Returned ancestor-first, so a resolver that simply took the last one seen would
        // pass this by accident; the ordering here is the one that catches it.
        stub(inherited, overridden);

        assertThat(resolver.effectiveFor(phones))
                .singleElement()
                .extracting(CategoryAttribute::getLabel)
                .isEqualTo("Phone brand");
    }

    @Test
    void ordersByGroupThenByPositionWithinTheGroup() {
        stub(attribute(phones, "charging", "Charging", "Battery", 4, 1),
                attribute(phones, "panel", "Panel", "Display", 1, 3),
                attribute(phones, "screen_size", "Screen size", "Display", 1, 0),
                attribute(phones, "capacity", "Capacity", "Battery", 4, 0));

        assertThat(resolver.effectiveFor(phones))
                .extracting(CategoryAttribute::getCode)
                .containsExactly("screen_size", "panel", "capacity", "charging");
    }

    /** "Other" is where the leftovers print, so it cannot come first. */
    @Test
    void ungroupedAttributesSinkBelowEveryNamedGroup() {
        CategoryAttribute loose = attribute(phones, "note", "Note", null, 0, 0);
        stub(loose, attribute(phones, "capacity", "Capacity", "Battery", 9, 0));

        assertThat(resolver.effectiveFor(phones))
                .extracting(CategoryAttribute::getCode)
                .containsExactly("capacity", "note");
    }

    /**
     * A group whose members disagree about its rank — half inherited from a category that
     * ranked it differently — still has to come out as one block.
     */
    @Test
    void aGroupTakesTheLowestRankAnyOfItsMembersCarries() {
        stub(attribute(electronics, "warranty", "Warranty", "General", 7, 0),
                attribute(phones, "brand", "Brand", "General", 0, 1),
                attribute(phones, "panel", "Panel", "Display", 3, 0));

        assertThat(resolver.effectiveFor(phones))
                .extracting(CategoryAttribute::getCode)
                .containsExactly("warranty", "brand", "panel");
    }

    /** A parent chain that loops is bad data, not a reason to hang the request. */
    @Test
    void survivesACycleInTheParentChain() {
        electronics.setParentCategory(phones);
        when(categoryAttributeRepository.findAllByCategory_UuidInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of());

        assertThat(resolver.effectiveFor(phones)).isEmpty();
    }

    // -------------------------------------------------------------------- lookup

    /** Both halves are normalised, so every spelling of the code lands on one key. */
    @Test
    void findsAnAttributeByCodeHoweverItWasTyped() {
        CategoryAttribute screenSize = attribute(phones, "screen_size", "Screen size", "Display", 1, 0);

        assertThat(CategoryAttributeResolver.lookup(List.of(screenSize)))
                .containsOnlyKeys("screen_size");
        // The map is keyed in the normalised form, so a caller normalises what it looks
        // up too — which is exactly what the validator does with the key a seller sent.
        assertThat(CategoryAttributeResolver.lookup(List.of(screenSize))
                .get(CategoryAttributeResolver.normaliseKey("Screen-Size")))
                .isSameAs(screenSize);
    }

    /** A label that is not just the code prettified is the case indexing labels buys. */
    @Test
    void findsAnAttributeByALabelThatDiffersFromItsCode() {
        CategoryAttribute ram = attribute(phones, "ram", "Memory size", "Performance", 2, 0);

        assertThat(CategoryAttributeResolver.lookup(List.of(ram)))
                .containsOnlyKeys("ram", "memory_size");
    }

    @Test
    void normalisesSpacesAndHyphensToTheUnderscoreCodesUse() {
        assertThat(CategoryAttributeResolver.normaliseKey("  Screen-Size ")).isEqualTo("screen_size");
        assertThat(CategoryAttributeResolver.normaliseKey("Refresh Rate")).isEqualTo("refresh_rate");
        assertThat(CategoryAttributeResolver.normaliseKey(null)).isEmpty();
    }

    /** A label colliding with another attribute's code must not shadow that attribute. */
    @Test
    void aCodeBeatsALabelOfTheSameName() {
        CategoryAttribute code = attribute(phones, "size", "Storage size", null, 0, 0);
        CategoryAttribute label = attribute(phones, "ram", "Size", null, 0, 0);

        assertThat(CategoryAttributeResolver.lookup(List.of(label, code)).get("size")).isSameAs(code);
    }

    // ------------------------------------------------------------------ fixtures

    private void stub(CategoryAttribute... attributes) {
        when(categoryAttributeRepository.findAllByCategory_UuidInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(attributes));
    }

    private static Category category(String slug, Category parent) {
        Category category = new Category();
        category.setUuid(UUID.randomUUID());
        category.setSlug(slug);
        category.setName(slug);
        category.setParentCategory(parent);
        return category;
    }

    private static CategoryAttribute attribute(Category owner, String code, String label,
                                               String group, int groupOrder, int sortOrder) {
        CategoryAttribute attribute = new CategoryAttribute();
        attribute.setUuid(UUID.randomUUID());
        attribute.setCategory(owner);
        attribute.setCode(code);
        attribute.setLabel(label);
        attribute.setGroupName(group);
        attribute.setGroupSortOrder(groupOrder);
        attribute.setSortOrder(sortOrder);
        return attribute;
    }
}
