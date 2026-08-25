package co.istad.projectpracticum.phsardigital.features.address.dto;

import co.istad.projectpracticum.phsardigital.features.address.AddressType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The fields belonging to the other shape come back null, so a client can render the
 * right form straight from {@code type} without guessing.
 *
 * @param formattedAddress the shape's fields as one line, the same text an order copies
 *                         at checkout. Derived — send the parts, not this
 */
public record AddressResponse(
        UUID id,
        AddressType type,
        String label,
        String recipient,
        String phone,
        String locationName,
        String streetNo,
        String province,
        String district,
        String commune,
        String village,
        String formattedAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isDefault,
        List<AddressPhotoResponse> landmarkPhotos
) {
}
