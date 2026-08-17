package co.istad.projectpracticum.phsardigital.features.seller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Every field is optional: this backs a PATCH, and the mapper ignores nulls so an
 * absent field means "leave it alone" rather than "clear it".
 */
public record SellerProfileUpdateRequest(
        @Size(max = 255, message = "Business name must not exceed 255 characters")
        String businessName,

        @Size(max = 100, message = "Business type must not exceed 100 characters")
        String businessType,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        /**
         * Object name of an already-uploaded public image. Replacing the logo deletes
         * the object behind the old one, so this is handled in the service rather than
         * by the mapper.
         */
        @Size(max = 512, message = "Logo object name must not exceed 512 characters")
        String logoObjectName,

        @Size(max = 2000, message = "Address must not exceed 2000 characters")
        String address,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "Province must not exceed 100 characters")
        String province,

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        BigDecimal longitude,

        @Size(max = 2000, message = "Google Maps URL must not exceed 2000 characters")
        String googleMapUrl
) {
}
