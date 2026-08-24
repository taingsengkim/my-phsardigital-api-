package co.istad.projectpracticum.phsardigital.features.address.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        String recipient,
        String phone,
        String line1,
        String line2,
        String city,
        String province,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isDefault,
        List<AddressPhotoResponse> landmarkPhotos
) {
}
