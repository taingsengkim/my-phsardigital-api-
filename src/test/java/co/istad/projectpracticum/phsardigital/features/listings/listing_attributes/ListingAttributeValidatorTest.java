package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeOption;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeResolver;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeValidator.SubmittedAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeValidator.ValidatedAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * What a seller may put in a spec, and what it is turned into on the way to the database.
 *
 * <p>The normalising half matters as much as the rejecting half: a facet filter compares
 * stored values, so "amoled" and "AMOLED" have to end up as the same string or the two
 * phones land on different shelves.
 */
@ExtendWith(MockitoExtension.class)
class ListingAttributeValidatorTest {

    @Mock
    private CategoryAttributeResolver categoryAttributeResolver;

    private ListingAttributeValidator validator;
    private Category phones;

    @BeforeEach
    void setUp() {
        validator = new ListingAttributeValidator(categoryAttributeResolver);
        phones = new Category();
        phones.setUuid(UUID.randomUUID());
        phones.setName("Phones");
        phones.setSlug("phones");
    }

    // --------------------------------------------------------------- free-form

    /**
     * A category with no schema is the state every category is in before an admin defines
     * one, so nothing may be rejected there.
     */
    @Test
    void acceptsAnythingWhenTheCategoryDefinesNothing() {
        schema();

        assertThat(validator.validate(phones, List.of(submitted("box_colour", "  blue  "))))
                .containsExactly(new ValidatedAttribute("box_colour", "blue", null));
    }

    @Test
    void keepsASpecTheCategoryDoesNotDefine() {
        schema(number("screen_size", "Screen size", "in", null, null));

        List<ValidatedAttribute> validated = validator.validate(phones, List.of(
                submitted("screen_size", "6.7"),
                submitted("box_colour", "blue")));

        assertThat(validated).extracting(ValidatedAttribute::key)
                .containsExactly("screen_size", "box_colour");
        assertThat(validated.get(1).definition()).isNull();
    }

    @Test
    void refusesABlankKeyOrValue() {
        schema();

        assertThatThrownBy(() -> validator.validate(phones, List.of(submitted(" ", "blue"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("key is required");
        assertThatThrownBy(() -> validator.validate(phones, List.of(submitted("colour", " "))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("value is required");
    }

    // -------------------------------------------------------------------- keys

    /** The seller types a label; the listing has to store the code all the same. */
    @Test
    void matchesADefinitionByLabelAndStoresItsCode() {
        schema(number("screen_size", "Screen size", "in", null, null));

        assertThat(validator.validate(phones, List.of(submitted("Screen size", "6.7"))))
                .singleElement()
                .extracting(ValidatedAttribute::key)
                .isEqualTo("screen_size");
    }

    @Test
    void catchesTwoSpellingsOfOneAttributeAsTheDuplicateTheyAre() {
        schema(number("screen_size", "Screen size", "in", null, null));

        assertThatThrownBy(() -> validator.validate(phones, List.of(
                submitted("screen_size", "6.7"),
                submitted("Screen Size", "6.1"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("the same attribute");
    }

    // ----------------------------------------------------------------- numbers

    @Test
    void refusesANumberThatIsNotOne() {
        schema(number("screen_size", "Screen size", "in", null, null));

        assertThatThrownBy(() -> validator.validate(phones, List.of(submitted("screen_size", "6.7 inch"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Screen size must be a number");
    }

    @Test
    void enforcesTheBoundsTheAttributeCarries() {
        schema(number("screen_size", "Screen size", "in", 3.0, 12.0));

        assertThatThrownBy(() -> validator.validate(phones, List.of(submitted("screen_size", "42"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at most 12 in");
        assertThatThrownBy(() -> validator.validate(phones, List.of(submitted("screen_size", "1"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least 3 in");
        assertThatCode(() -> validator.validate(phones, List.of(submitted("screen_size", "6.7"))))
                .doesNotThrowAnyException();
    }

    /** Two sellers typing the same size must end up filterable as the same value. */
    @Test
    void normalisesTrailingZerosOffANumber() {
        schema(number("screen_size", "Screen size", "in", null, null));

        assertThat(valueOf("screen_size", "6.70")).isEqualTo("6.7");
        assertThat(valueOf("screen_size", "6.000")).isEqualTo("6");
    }

    /** Stripping zeros off a round number leaves a BigDecimal that prints as 1E+2. */
    @Test
    void writesARoundNumberPlainlyRatherThanInScientificNotation() {
        schema(number("battery", "Battery", "mAh", null, null));

        assertThat(valueOf("battery", "5000")).isEqualTo("5000");
    }

    // ---------------------------------------------------------------- booleans

    @Test
    void acceptsTheUsualWaysOfWritingYesAndNo() {
        schema(of("dual_sim", "Dual SIM", AttributeDataType.BOOLEAN));

        assertThat(valueOf("dual_sim", "yes")).isEqualTo("true");
        assertThat(valueOf("dual_sim", "TRUE")).isEqualTo("true");
        assertThat(valueOf("dual_sim", "0")).isEqualTo("false");
        assertThat(valueOf("dual_sim", "No")).isEqualTo("false");
    }

    @Test
    void refusesABooleanThatIsNeither() {
        schema(of("dual_sim", "Dual SIM", AttributeDataType.BOOLEAN));

        assertThatThrownBy(() -> validator.validate(phones, List.of(submitted("dual_sim", "maybe"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must be true or false");
    }

    // ----------------------------------------------------------------- choices

    @Test
    void refusesAValueThatIsNotOnTheOptionListAndSaysWhatIs() {
        schema(select("panel", "Panel type", "AMOLED", "IPS LCD"));

        assertThatThrownBy(() -> validator.validate(phones, List.of(submitted("panel", "Plasma"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AMOLED, IPS LCD");
    }

    @Test
    void answersWithTheCanonicalSpellingOfAnOption() {
        schema(select("panel", "Panel type", "AMOLED", "IPS LCD"));

        assertThat(valueOf("panel", "amoled")).isEqualTo("AMOLED");
    }

    @Test
    void matchesAnOptionByItsLabelToo() {
        CategoryAttribute ram = select("ram", "RAM", "8");
        ram.getOptions().getFirst().setLabel("8 GB");
        schema(ram);

        assertThat(valueOf("ram", "8 GB")).isEqualTo("8");
    }

    @Test
    void takesSeveralValuesForAMultiSelectAndDropsTheRepeats() {
        CategoryAttribute colours = select("colours", "Colours", "Black", "White", "Blue");
        colours.setDataType(AttributeDataType.MULTI_SELECT);
        schema(colours);

        assertThat(valueOf("colours", "black, WHITE ,black")).isEqualTo("Black, White");
    }

    /**
     * A dropdown an admin emptied is a half-finished edit. Refusing the seller's listing
     * over it would make one careless PATCH break every posting in the category.
     */
    @Test
    void fallsBackToPlainTextWhenAChoiceHasNoOptionsLeft() {
        CategoryAttribute panel = of("panel", "Panel type", AttributeDataType.SELECT);
        schema(panel);

        assertThat(valueOf("panel", "Plasma")).isEqualTo("Plasma");
    }

    // ---------------------------------------------------------------- required

    @Test
    void refusesAListingMissingASpecTheCategoryInsistsOn() {
        CategoryAttribute storage = select("storage", "Storage", "128 GB");
        storage.setRequired(true);
        schema(storage, number("screen_size", "Screen size", "in", null, null));

        assertThatThrownBy(() -> validator.validate(phones, List.of(submitted("screen_size", "6.7"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Phones listings must specify Storage (storage)");
    }

    @Test
    void countsARequiredSpecAnsweredUnderItsLabelAsAnswered() {
        CategoryAttribute storage = select("storage", "Storage", "128 GB");
        storage.setRequired(true);
        schema(storage);

        assertThatCode(() -> validator.validate(phones, List.of(submitted("Storage", "128 GB"))))
                .doesNotThrowAnyException();
    }

    /** Nothing submitted at all is the case that has to fail, not the case that skips. */
    @Test
    void refusesAnEmptySubmissionWhenTheCategoryRequiresSomething() {
        CategoryAttribute storage = select("storage", "Storage", "128 GB");
        storage.setRequired(true);
        schema(storage);

        assertThatThrownBy(() -> validator.validate(phones, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must specify Storage");
        assertThatThrownBy(() -> validator.validate(phones, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must specify Storage");
    }

    // ------------------------------------------------------------------ length

    @Test
    void refusesAValueTooLongForTheColumn() {
        schema();

        assertThatThrownBy(() -> validator.validate(phones,
                List.of(submitted("note", "x".repeat(101)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("100 characters");
    }

    // ---------------------------------------------------------------- fixtures

    private String valueOf(String key, String rawValue) {
        return validator.validate(phones, List.of(submitted(key, rawValue))).getFirst().value();
    }

    private static SubmittedAttribute submitted(String key, String value) {
        return new SubmittedAttribute(key, value);
    }

    private void schema(CategoryAttribute... attributes) {
        when(categoryAttributeResolver.effectiveFor(any())).thenReturn(List.of(attributes));
    }

    private static CategoryAttribute of(String code, String label, AttributeDataType dataType) {
        CategoryAttribute attribute = new CategoryAttribute();
        attribute.setUuid(UUID.randomUUID());
        attribute.setCode(code);
        attribute.setLabel(label);
        attribute.setDataType(dataType);
        return attribute;
    }

    private static CategoryAttribute number(String code, String label, String unit,
                                            Double min, Double max) {
        CategoryAttribute attribute = of(code, label, AttributeDataType.NUMBER);
        attribute.setUnit(unit);
        attribute.setMinValue(min);
        attribute.setMaxValue(max);
        return attribute;
    }

    private static CategoryAttribute select(String code, String label, String... values) {
        CategoryAttribute attribute = of(code, label, AttributeDataType.SELECT);
        for (int i = 0; i < values.length; i++) {
            CategoryAttributeOption option = new CategoryAttributeOption();
            option.setAttribute(attribute);
            option.setValue(values[i]);
            option.setSortOrder(i);
            attribute.getOptions().add(option);
        }
        return attribute;
    }
}
