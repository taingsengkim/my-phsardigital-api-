package co.istad.projectpracticum.phsardigital.features.listings.dto;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Every field is optional: null means "leave it alone". Which is why this cannot take a
 * discount off — {@code DELETE /listings/{uuid}/discount} does that.
 *
 * @param fullPrice     the new list price, still accepted as {@code price}
 * @param discountPrice must be below the list price the listing ends up with
 * @param listingAttributes when given, replaces the listing's specs outright rather than
 *                          merging into them — which is what makes moving a listing to a
 *                          category with a different schema possible in one call, since
 *                          the new category's required specs can be supplied alongside
 *                          the move. Absent, the existing specs are re-checked against
 *                          the new category and a move that would leave one missing is
 *                          refused
 */
public record UpdateListingRequest(
        UUID categoryUuid,
        String title,
        String description,

        /* Blank clears the code; null leaves it as it was. */
        @Size(max = 64, message = "SKU must not exceed 64 characters")
        String sku,

        @DecimalMin(value = "0.0", inclusive = true, message = "Cost price must not be negative")
        BigDecimal costPrice,
        @JsonAlias("price")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        BigDecimal fullPrice,
        @DecimalMin(value = "0.0", inclusive = true, message = "Discount price must not be negative")
        BigDecimal discountPrice,
        @PositiveOrZero(message = "Stock quantity must not be negative")
        Integer stockQty,
        ListingStatus status,
        Boolean isFeatured,
        @Valid
        List<ListingAttributeCreateRequest> listingAttributes
) {
}
