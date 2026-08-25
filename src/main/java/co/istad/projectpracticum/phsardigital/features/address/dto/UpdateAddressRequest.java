package co.istad.projectpracticum.phsardigital.features.address.dto;

import co.istad.projectpracticum.phsardigital.features.address.AddressType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every field is optional: this backs a PATCH, so an absent field means "leave it
 * alone" rather than "clear it". The fields a shape requires cannot be blanked here —
 * sending {@code ""} for one is refused the same way omitting it on create is.
 */
public record UpdateAddressRequest(
        /**
         * Switches the address between shapes. Doing so drops whatever belonged only to
         * the old shape — a CITY address turned PROVINCE keeps no location name or
         * street no. — so the same call must carry what the new shape needs.
         *
         * <p>Absent leaves the shape as it is, and the fields sent are then checked
         * against that.
         */
        AddressType type,

        @Size(max = 50, message = "Label must not exceed 50 characters")
        String label,

        @Size(max = 255, message = "Recipient must not exceed 255 characters")
        String recipient,

        @Size(max = 30, message = "Phone must not exceed 30 characters")
        String phone,

        @Size(max = 255, message = "Location name must not exceed 255 characters")
        String locationName,

        @Size(max = 50, message = "Street No. must not exceed 50 characters")
        String streetNo,

        @Size(max = 100, message = "Province must not exceed 100 characters")
        String province,

        @Size(max = 100, message = "District must not exceed 100 characters")
        String district,

        @Size(max = 100, message = "Commune must not exceed 100 characters")
        String commune,

        @Size(max = 100, message = "Village must not exceed 100 characters")
        String village,

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
        Boolean isDefault,

        /**
         * Replaces the whole set of landmark photos when present, because a PATCH that
         * could only ever add would leave no way to remove one. Absent means unchanged;
         * an empty list clears them.
         */
        @Valid
        @Size(max = 3, message = "An address may keep at most 3 landmark photos")
        List<AddressPhotoRequest> landmarkPhotos
) {
}
