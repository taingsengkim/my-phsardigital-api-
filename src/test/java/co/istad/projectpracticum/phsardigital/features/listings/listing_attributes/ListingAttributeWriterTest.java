package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingAttributeWriterTest {

    @Mock
    private ListingAttributeValidator validator;

    private ListingAttributeWriter writer;
    private Listing listing;

    @BeforeEach
    void setUp() {
        writer = new ListingAttributeWriter(validator);
        listing = new Listing();
        listing.setCategory(new Category());
        listing.setListingAttributes(new ArrayList<>());
    }

    @Test
    void writesEachNormalisedMultiSelectChoiceBesideTheDisplayString() {
        CategoryAttribute definition = new CategoryAttribute();
        when(validator.validate(any(), any())).thenReturn(List.of(
                new ListingAttributeValidator.ValidatedAttribute(
                        "colours", "Black, White", definition,
                        new LinkedHashSet<>(List.of("Black", "White")))));

        List<ListingAttribute> result = writer.apply(listing,
                List.of(new ListingAttributeCreateRequest(
                        "colours", "black, white", null)));

        assertThat(result).singleElement().satisfies(attribute -> {
            assertThat(attribute.getValue()).isEqualTo("Black, White");
            assertThat(attribute.getSelectedValues()).containsExactly("Black", "White");
            assertThat(attribute.getDefinition()).isSameAs(definition);
        });
    }

    @Test
    void clearsOldSelectedValuesWhenAnAttributeBecomesScalar() {
        ListingAttribute existing = new ListingAttribute();
        existing.setListing(listing);
        existing.setKey("colours");
        existing.setValue("Black, White");
        existing.getSelectedValues().addAll(List.of("Black", "White"));
        listing.getListingAttributes().add(existing);

        when(validator.validate(any(), any())).thenReturn(List.of(
                new ListingAttributeValidator.ValidatedAttribute(
                        "colours", "Black", null)));

        List<ListingAttribute> result = writer.apply(listing,
                List.of(new ListingAttributeCreateRequest("colours", "Black", null)));

        assertThat(result).containsExactly(existing);
        assertThat(existing.getValue()).isEqualTo("Black");
        assertThat(existing.getSelectedValues()).isEmpty();
    }
}
