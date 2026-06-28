package co.istad.sengkim.phsardigital.features.listings.dto;

import co.istad.sengkim.phsardigital.features.listings.listing_images.dto.ListingImageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record ListingCreateRequest(
        @NotNull(message = "Category must not be null")
        UUID categoryUuid,
        @NotBlank(message = "Title must not be blank")
        String title,
        @NotBlank(message = "Title must not be blank")
        String slug,
        String description,
        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        Double price,
        @NotNull(message = "Stock quantity must not be null")
        @PositiveOrZero(message = "Stock quantity must not be negative")
        Integer stockQty,
        Boolean isFeatured,
        @NotNull(message = "Thumbnail must not be null")
        String thumbnailUrl,
        @Valid
        List<ListingImageRequest> images
) {
}
