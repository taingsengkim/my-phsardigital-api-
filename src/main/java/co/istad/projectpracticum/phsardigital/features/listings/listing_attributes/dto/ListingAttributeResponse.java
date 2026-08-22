package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;

import java.util.UUID;

/**
 * One spec on a product page.
 *
 * <p>Everything from {@code label} down is read off the category's definition of this
 * attribute, so a storefront can print "Screen size — 6.7 in" under a Display heading
 * without knowing anything about phones.
 *
 * @param key      the stored key, e.g. {@code screen_size}
 * @param label    what to print; falls back to a tidied-up {@code key} for a custom spec
 * @param unit     printed after the value, e.g. {@code in}; null when there is none
 * @param group    the spec-table section; null for a custom spec, which prints last
 * @param dataType null for a custom spec, which is by definition untyped
 * @param custom   true when the category does not define this key — a spec the seller
 *                 added themselves, kept but never required or filtered on
 */
public record ListingAttributeResponse(
        UUID uuid,
        String key,
        String label,
        String value,
        String unit,
        String group,
        AttributeDataType dataType,
        Integer sortOrder,
        Boolean custom,
        UUID listingUuid
) {
}
