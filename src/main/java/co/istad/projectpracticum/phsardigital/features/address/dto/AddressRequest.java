package co.istad.projectpracticum.phsardigital.features.address.dto;

import co.istad.projectpracticum.phsardigital.features.address.AddressType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param type           which shape this address takes, and so which of the fields
 *                       below apply. {@code PROVINCE} wants province, district, commune
 *                       and village; {@code CITY} wants a location name, street no.,
 *                       district, commune and village. Sending a field the chosen shape
 *                       does not use is rejected rather than ignored, so a form that
 *                       switched shape mid-fill cannot leave the other half behind
 * @param locationName   CITY only: the building, borey or shop to look for
 * @param streetNo       CITY only, stored and shown exactly as typed
 * @param province       PROVINCE only
 * @param recipient      who receives the delivery, when that is not the account holder
 * @param isDefault      when true, the address this user's next checkout picks by
 *                       default; the first address a user saves becomes their default
 *                       regardless
 * @param landmarkPhotos optional shots of the place for the courier, already uploaded
 *                       through the image endpoint. Capped at three: past that they
 *                       stop being landmarks and start being an album
 */
public record AddressRequest(
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

        Boolean isDefault,

        @Valid
        @Size(max = 3, message = "An address may keep at most 3 landmark photos")
        List<AddressPhotoRequest> landmarkPhotos
) {
}
