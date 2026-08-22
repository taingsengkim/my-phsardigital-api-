package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Locale;

/**
 * The display half of an attribute comes off the category's definition, not off the row:
 * {@code screen_size} is stored, "Screen size" is printed, and renaming the label must not
 * mean rewriting every listing.
 */
@Mapper(componentModel = "spring")
public abstract class ListingAttributeMapper {

    @Mapping(target = "listingUuid", source = "listing.uuid")
    @Mapping(target = "label", expression = "java(label(listingAttribute))")
    @Mapping(target = "unit", source = "definition.unit")
    @Mapping(target = "group", source = "definition.groupName")
    @Mapping(target = "dataType", source = "definition.dataType")
    @Mapping(target = "custom", expression = "java(listingAttribute.getDefinition() == null)")
    public abstract ListingAttributeResponse toResponse(ListingAttribute listingAttribute);

    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "listing", ignore = true)
    @Mapping(target = "definition", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    public abstract ListingAttribute fromCreateRequest(ListingAttributeCreateRequest request);

    /**
     * The definition's label, or the key made readable — {@code screen_size} prints as
     * "Screen size" rather than being shown to a shopper raw.
     */
    protected String label(ListingAttribute attribute) {
        if (attribute.getDefinition() != null) {
            return attribute.getDefinition().getLabel();
        }
        String key = attribute.getKey();
        if (key == null || key.isBlank()) {
            return key;
        }
        String words = key.trim().replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }
}
