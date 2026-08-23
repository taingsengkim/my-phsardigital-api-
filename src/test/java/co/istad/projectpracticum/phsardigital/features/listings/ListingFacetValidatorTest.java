package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeOption;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeResolver;
import co.istad.projectpracticum.phsardigital.features.listings.dto.AttributeFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingFacetValidatorTest {

    @Mock
    private CategoryAttributeResolver resolver;

    private ListingFacetValidator validator;
    private Category phones;

    @BeforeEach
    void setUp() {
        validator = new ListingFacetValidator(resolver);
        phones = new Category();
        phones.setUuid(UUID.randomUUID());
        phones.setSlug("phones");
    }

    @Test
    void canonicalisesNumbersBooleansAndOptionLabels() {
        CategoryAttribute size = definition("screen_size", AttributeDataType.NUMBER, true);
        CategoryAttribute dualSim = definition("dual_sim", AttributeDataType.BOOLEAN, true);
        CategoryAttribute ram = definition("ram", AttributeDataType.SELECT, true);
        option(ram, "8", "8 GB");
        when(resolver.effectiveFor(phones)).thenReturn(List.of(size, dualSim, ram));

        List<AttributeFilter> result = validator.validate(phones, List.of(
                filter("screen_size", "6.70"),
                filter("dual_sim", "yes"),
                filter("ram", "8 gb")));

        assertThat(result).extracting(filter -> filter.values().iterator().next())
                .containsExactly("6.7", "true", "8");
    }

    @Test
    void acceptsEachSelectedValueForAMultiSelectFacet() {
        CategoryAttribute colours = definition("colours", AttributeDataType.MULTI_SELECT, true);
        option(colours, "Black", null);
        option(colours, "White", null);
        when(resolver.effectiveFor(phones)).thenReturn(List.of(colours));

        AttributeFilter result = validator.validate(phones,
                List.of(new AttributeFilter("colours",
                        new LinkedHashSet<>(List.of("black", "WHITE")))))
                .getFirst();

        assertThat(result.values()).containsExactly("Black", "White");
    }

    @Test
    void rejectsUnknownAndNonFilterableDefinitions() {
        CategoryAttribute serial = definition("serial_number", AttributeDataType.TEXT, false);
        when(resolver.effectiveFor(phones)).thenReturn(List.of(serial));

        assertThatThrownBy(() -> validator.validate(
                phones, List.of(filter("serial_number", "abc"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not filterable");
        assertThatThrownBy(() -> validator.validate(
                phones, List.of(filter("unknown", "abc"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown attribute filter");
    }

    private static AttributeFilter filter(String key, String value) {
        return new AttributeFilter(key, Set.of(value));
    }

    private static CategoryAttribute definition(String code, AttributeDataType type,
                                                boolean filterable) {
        CategoryAttribute attribute = new CategoryAttribute();
        attribute.setUuid(UUID.randomUUID());
        attribute.setCode(code);
        attribute.setLabel(code);
        attribute.setDataType(type);
        attribute.setFilterable(filterable);
        return attribute;
    }

    private static void option(CategoryAttribute attribute, String value, String label) {
        CategoryAttributeOption option = new CategoryAttributeOption();
        option.setAttribute(attribute);
        option.setValue(value);
        option.setLabel(label);
        attribute.getOptions().add(option);
    }
}
