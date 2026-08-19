package co.istad.projectpracticum.phsardigital.features.listings.dto;

import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ListingImageRequest;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * @param fullPrice     the list price, still accepted under its old name {@code price}
 * @param discountPrice optional sale price; must be below {@code fullPrice}
 */
public record ListingCreateRequest(

        @NotNull(message = "Category must not be null")
        UUID categoryUuid,

        @NotBlank(message = "Title must not be blank")
        String title,

        String description,

        @JsonAlias("price")
        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        Double fullPrice,

        @DecimalMin(value = "0.0", inclusive = true, message = "Discount price must not be negative")
        Double discountPrice,

        @NotNull(message = "Stock quantity must not be null")
        @PositiveOrZero(message = "Stock quantity must not be negative")
        Integer stockQty,

        Boolean isFeatured,

        @NotNull(message = "Thumbnail must not be null")
        String thumbnailObjectName,

        @Valid
        List<ListingImageRequest> images ,

        @Valid
        List<ListingAttributeCreateRequest> listingAttributes
) {
}
