package co.istad.projectpracticum.phsardigital.features.seller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every field is optional: this backs a PATCH, and the mapper ignores nulls so an
 * absent field means "leave it alone" rather than "clear it".
 */
public record SellerProfileUpdateRequest(
        /**
         * Optional, but not erasable. {@code @NotBlank} would reject the absent field
         * too, and absent has to keep meaning "leave it alone"; {@code @Pattern} passes
         * null through and only judges a value actually sent. Without it {@code ""}
         * satisfied {@code @Size} and the mapper wrote it, leaving the shop nameless on
         * the Top Sellers board and every listing card.
         *
         * <p>DOTALL so a name written across two lines is not read as blank.
         */
        @Pattern(regexp = ".*\\S.*", flags = Pattern.Flag.DOTALL,
                message = "Business name must not be blank")
        @Size(max = 255, message = "Business name must not exceed 255 characters")
        String businessName,

        @Size(max = 100, message = "Business type must not exceed 100 characters")
        String businessType,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        /**
         * Object name of an already-uploaded public image. Replacing the logo deletes
         * the object behind the old one, so this is handled in the service rather than
         * by the mapper. The cover below works the same way.
         */
        @Size(max = 512, message = "Logo object name must not exceed 512 characters")
        String logoObjectName,

        @Size(max = 512, message = "Cover object name must not exceed 512 characters")
        String coverObjectName,

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
        String googleMapUrl,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phoneNumber,

        @Size(max = 1000, message = "Biography must not exceed 1000 characters")
        String biography,

        @Size(max = 10, message = "A shop may list at most 10 social links")
        List<@Size(max = 500, message = "Each social link must not exceed 500 characters") String> socialLink
) {
}
