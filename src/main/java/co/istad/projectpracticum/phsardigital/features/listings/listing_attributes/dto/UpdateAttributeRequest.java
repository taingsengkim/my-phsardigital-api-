package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// In listing_attributes/dto/UpdateAttributeRequest.java
public record UpdateAttributeRequest(
        @NotNull(message = "Attribute UUID is required")
        UUID attributeUuid,

        String newKey,
        String newValue   
) {}