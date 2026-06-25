package co.istad.sengkim.phsardigital.features.listings.dto;

import co.istad.sengkim.phsardigital.features.listing_images.ListingImageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record ListingRequest(
        @NotNull(message = "Category must not be null")
        UUID categoryUuid,
        @NotBlank(message = "Title must not be blank")
        String title,
        String description,
        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        Double price,
        @NotNull(message = "Stock quantity must not be null")
        @PositiveOrZero(message = "Stock quantity must not be negative")
        Integer stockQty,
        Boolean isFeatured,
        @Valid
        List<ListingImageRequest> images
) {
}
