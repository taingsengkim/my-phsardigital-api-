package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseResponse(
        UUID uuid,
        String buyerId,
        String sellerId,
        String businessName,
        Double totalPrice,
        PurchaseStatus status,
        String shippingAddress,
        String note,
        List<PurchaseItemResponse> items,
        LocalDateTime createdAt
) {
}