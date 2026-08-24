package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseChannel;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param buyerName  who to ask for on delivery
 * @param buyerPhone how to reach them when the address does not resolve — orders are
 *                   settled cash on delivery, so without it the seller has a street
 *                   address and no way to call. Only ever answered to that order's own
 *                   buyer or its seller; every route to this DTO checks one or the other
 */
public record PurchaseResponse(
        UUID uuid,
        String buyerId,
        String buyerName,
        String buyerPhone,
        String sellerId,
        String businessName,
        BigDecimal totalPrice,
        PurchaseStatus status,
        PurchaseChannel channel,
        String shippingAddress,
        List<DeliveryPhotoResponse> deliveryPhotos,
        String note,
        List<PurchaseItemResponse> items,
        LocalDateTime createdAt
) {
}