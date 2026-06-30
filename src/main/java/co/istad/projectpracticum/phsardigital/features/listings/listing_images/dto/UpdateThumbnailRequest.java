package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateThumbnailRequest(
        @NotBlank(message = "Object name must not be blank")
        String objectName
) {}