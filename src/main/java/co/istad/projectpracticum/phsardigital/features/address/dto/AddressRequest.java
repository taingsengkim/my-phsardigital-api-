package co.istad.projectpracticum.phsardigital.features.address.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param recipient who receives the delivery, when that is not the account holder
 * @param isDefault when true, the address this user's next checkout picks by default;
 *                  the first address a user saves becomes their default regardless
 */
public record AddressRequest(
        @Size(max = 50, message = "Label must not exceed 50 characters")
        String label,

        @Size(max = 255, message = "Recipient must not exceed 255 characters")
        String recipient,

        @Size(max = 30, message = "Phone must not exceed 30 characters")
        String phone,

        @NotBlank(message = "Address line 1 is required")
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

        Boolean isDefault
) {
}
