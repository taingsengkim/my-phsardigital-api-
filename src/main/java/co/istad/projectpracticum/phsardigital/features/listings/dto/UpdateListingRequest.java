package co.istad.projectpracticum.phsardigital.features.listings.dto;

import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record UpdateListingRequest(
        UUID categoryUuid,
        String title,
        String description,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        Double price,
        @PositiveOrZero(message = "Stock quantity must not be negative")
        Integer stockQty,
        ListingStatus status,
        Boolean isFeatured
) {
}