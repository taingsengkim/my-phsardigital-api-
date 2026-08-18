package co.istad.projectpracticum.phsardigital.features.seller.dto;

import jakarta.persistence.Id;

import java.util.List;

/**
 * The shop block embedded in a listing, a review and a reply. Carries
 * {@code businessName} and {@code logoUri} because they are what a product card
 * renders — without them, printing a store name costs a {@code /sellers/{id}} call per
 * listing.
 */
public record SellerProfileSummaryResponse(
        @Id
        String sellerId,
        String businessName,
        String logoUri,
        String phoneNumber,
        String biography,
        List<String>socialLink
) {
}
