package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import java.util.UUID;

public record PurchaseItemResponse(
        UUID listingUuid,
        String title,
        Integer quantity,
        Double unitPrice,
        Double lineTotal
) {
}