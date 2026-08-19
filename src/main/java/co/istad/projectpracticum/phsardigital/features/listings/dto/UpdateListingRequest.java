package co.istad.projectpracticum.phsardigital.features.listings.dto;

import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * Every field is optional: null means "leave it alone". Which is why this cannot take a
 * discount off — {@code DELETE /listings/{uuid}/discount} does that.
 *
 * @param fullPrice     the new list price, still accepted as {@code price}
 * @param discountPrice must be below the list price the listing ends up with
 */
public record UpdateListingRequest(
        UUID categoryUuid,
        String title,
        String description,
        @JsonAlias("price")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        Double fullPrice,
        @DecimalMin(value = "0.0", inclusive = true, message = "Discount price must not be negative")
        Double discountPrice,
        @PositiveOrZero(message = "Stock quantity must not be negative")
        Integer stockQty,
        ListingStatus status,
        Boolean isFeatured
) {
}
