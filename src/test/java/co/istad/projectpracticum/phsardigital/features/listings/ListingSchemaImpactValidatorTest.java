package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeResolver;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeWriter;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSchemaImpactValidatorTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ListingAttributeWriter writer;
    @Mock
    private CategoryAvailability categoryAvailability;
    @Mock
    private CategoryAttributeResolver categoryAttributeResolver;

    private ListingSchemaImpactValidator validator;
    private Category root;
    private Category child;

    @BeforeEach
    void setUp() {
        validator = new ListingSchemaImpactValidator(
                listingRepository, writer, categoryAvailability, categoryAttributeResolver);

        root = category(UUID.randomUUID());
        child = category(UUID.randomUUID());
        root.getChildCategories().add(child);
        child.setParentCategory(root);
    }

    @Test
    void validatesPublishedListingsAcrossTheSubtree() {
        Listing listing = listing(child, ListingStatus.ACTIVE);
        List<ListingAttributeCreateRequest> current = List.of();
        when(listingRepository.findAllByCategory_UuidIn(
                org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(listing));
        when(categoryAvailability.isEffectivelyActive(child)).thenReturn(true);
        when(writer.currentOf(listing)).thenReturn(current);

        validator.revalidatePublishedListings(root);

        verify(writer).apply(listing, current);
    }

    @Test
    void skipsListingsThatAreNotPublicByStatusOrCategory() {
        Listing draft = listing(child, ListingStatus.DRAFT);
        Listing hidden = listing(child, ListingStatus.ACTIVE);
        when(listingRepository.findAllByCategory_UuidIn(
                org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(draft, hidden));
        when(categoryAvailability.isEffectivelyActive(child)).thenReturn(false);

        validator.revalidatePublishedListings(root);

        verify(writer, never()).currentOf(org.mockito.ArgumentMatchers.any());
        verify(writer, never()).apply(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reportsTheListingThatWouldBecomeInvalid() {
        Listing listing = listing(child, ListingStatus.SOLD_OUT);
        listing.setUuid(UUID.randomUUID());
        List<ListingAttributeCreateRequest> current = List.of();
        when(listingRepository.findAllByCategory_UuidIn(
                org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(listing));
        when(categoryAvailability.isEffectivelyActive(child)).thenReturn(true);
        when(writer.currentOf(listing)).thenReturn(current);
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Storage is required"))
                .when(writer).apply(listing, current);

        assertThatThrownBy(() -> validator.revalidatePublishedListings(root))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining(listing.getUuid().toString())
                .hasMessageContaining("Storage is required");
    }

    @Test
    void rejectsSchemasWithMoreRequiredFieldsThanAListingCanSubmit() {
        root.setSlug("electronics");
        List<CategoryAttribute> required = java.util.stream.IntStream
                .rangeClosed(1, 101)
                .mapToObj(index -> requiredAttribute())
                .toList();
        when(categoryAttributeResolver.effectiveFor(root)).thenReturn(required);

        assertThatThrownBy(() -> validator.revalidatePublishedListings(root))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining("101 required effective attributes")
                .hasMessageContaining("at most 100");

        verify(listingRepository, never())
                .findAllByCategory_UuidIn(org.mockito.ArgumentMatchers.anyCollection());
    }

    private static Category category(UUID uuid) {
        Category category = new Category();
        category.setUuid(uuid);
        category.setChildCategories(new ArrayList<>());
        return category;
    }

    private static Listing listing(Category category, ListingStatus status) {
        Listing listing = new Listing();
        listing.setCategory(category);
        listing.setStatus(status);
        return listing;
    }

    private static CategoryAttribute requiredAttribute() {
        CategoryAttribute attribute = new CategoryAttribute();
        attribute.setRequired(true);
        return attribute;
    }
}
