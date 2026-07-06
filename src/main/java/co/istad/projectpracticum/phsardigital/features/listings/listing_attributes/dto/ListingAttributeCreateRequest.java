package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto;

public record ListingAttributeCreateRequest(
        String key ,
        String value ,
        Integer sortOrder

) {
}
