package co.istad.projectpracticum.phsardigital.features.cart.dto;

import java.util.UUID;
public record CartItemResponse(
        UUID uuid,
        UUID listingUuid,
        String title,
        Double unitPrice,
        Integer quantity,
        Double lineTotal
) {
}