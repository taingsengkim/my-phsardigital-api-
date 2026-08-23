package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeResolver;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The one place a listing's specs are written.
 *
 * <p>It always takes the complete set the listing should end up with, never a delta —
 * adding one attribute is "here is everything, including the new one". Two rules force
 * that shape: the category's required specs can only be checked against the final state,
 * and normalising a value depends on the definition, which depends on the category. A
 * caller holding a delta turns it into a final state first; {@link #currentOf} is how.
 */
@Component
@RequiredArgsConstructor
public class ListingAttributeWriter {

    private final ListingAttributeValidator listingAttributeValidator;

    /**
     * Validates the given specs against the listing's category and writes them, replacing
     * whatever was there.
     *
     * <p>Rows are matched by key and reused rather than deleted and re-created. Within one
     * flush Hibernate inserts before it deletes, so a wholesale replacement would collide
     * with the {@code (listing_uuid, key)} constraint against the row on its way out.
     */
    public List<ListingAttribute> apply(Listing listing, List<ListingAttributeCreateRequest> requests) {
        List<ListingAttributeCreateRequest> submitted = requests == null ? List.of() : requests;

        List<ListingAttributeValidator.ValidatedAttribute> validated = listingAttributeValidator.validate(
                listing.getCategory(),
                submitted.stream()
                        .map(request -> new ListingAttributeValidator.SubmittedAttribute(
                                request.key(), request.value()))
                        .toList());

        if (listing.getListingAttributes() == null) {
            listing.setListingAttributes(new ArrayList<>());
        }
        List<ListingAttribute> attributes = listing.getListingAttributes();
        Map<String, ListingAttribute> existing = attributes.stream().collect(Collectors.toMap(
                attribute -> CategoryAttributeResolver.normaliseKey(attribute.getKey()),
                Function.identity(),
                (first, duplicate) -> first));

        List<ListingAttribute> replacement = new ArrayList<>();
        for (int i = 0; i < validated.size(); i++) {
            ListingAttributeValidator.ValidatedAttribute value = validated.get(i);
            ListingAttribute attribute = existing.get(CategoryAttributeResolver.normaliseKey(value.key()));
            if (attribute == null) {
                attribute = new ListingAttribute();
                attribute.setListing(listing);
            }
            attribute.setKey(value.key());
            attribute.setValue(value.value());
            attribute.setDefinition(value.definition());
            if (attribute.getSelectedValues() == null) {
                attribute.setSelectedValues(new LinkedHashSet<>());
            } else {
                attribute.getSelectedValues().clear();
            }
            attribute.getSelectedValues().addAll(value.selectedValues());
            Integer sortOrder = submitted.get(i).sortOrder();
            attribute.setSortOrder(sortOrder != null ? sortOrder : i);
            replacement.add(attribute);
        }

        // Cleared in place: orphan removal only sees what happens to the collection
        // Hibernate is tracking, not to one swapped in for it.
        attributes.clear();
        attributes.addAll(replacement);
        return attributes;
    }

    /** The listing's specs as they stand, in the shape {@link #apply} takes. */
    public List<ListingAttributeCreateRequest> currentOf(Listing listing) {
        if (listing.getListingAttributes() == null) {
            return new ArrayList<>();
        }
        return listing.getListingAttributes().stream()
                .map(attribute -> new ListingAttributeCreateRequest(
                        attribute.getKey(), attribute.getValue(), attribute.getSortOrder()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
