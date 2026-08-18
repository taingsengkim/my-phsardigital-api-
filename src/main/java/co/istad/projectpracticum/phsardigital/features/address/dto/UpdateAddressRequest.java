package co.istad.projectpracticum.phsardigital.features.address.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Every field is optional: this backs a PATCH, so an absent field means "leave it
 * alone" rather than "clear it". {@code line1} is required on create and cannot be
 * blanked here, which is why it carries no {@code @NotBlank} — null simply means
 * unchanged.
 */
public record UpdateAddressRequest(
        @Size(max = 50, message = "Label must not exceed 50 characters")
        String label,

        @Size(max = 255, message = "Recipient must not exceed 255 characters")
        String recipient,

        @Size(max = 30, message = "Phone must not exceed 30 characters")
        String phone,

        @Size(max = 2000, message = "Address line 1 must not exceed 2000 characters")
        String line1,

        @Size(max = 2000, message = "Address line 2 must not exceed 2000 characters")
        String line2,

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

        /**
         * Promotes this address to the account's default, so "edit it and make it my
         * default" is one call rather than two that can half-succeed.
         *
         * <p>Only {@code true} acts. Clearing the flag without naming a replacement
         * would leave the account with addresses but nothing for checkout to pick, so
         * moving the default means sending {@code true} on the one that should have it.
         */
        Boolean isDefault
) {
}
