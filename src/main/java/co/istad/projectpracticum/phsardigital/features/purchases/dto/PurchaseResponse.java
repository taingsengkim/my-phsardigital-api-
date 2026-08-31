package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.features.purchases.PaymentMethod;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseChannel;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param buyerName         who to ask for on delivery. The recipient named on the order
 *                          when there is one, falling back to the account holder —
 *                          people order to a parent's or a colleague's address, and it
 *                          is the recipient the courier has to ask for.
 * @param buyerPhone        how to reach them when the address does not resolve — orders
 *                          are settled cash on delivery, so without it the seller has a
 *                          street address and no way to call. Only ever answered to that
 *                          order's own buyer or its seller; every route to this DTO
 *                          checks one or the other
 * @param storeLogoUrl      the shop's logo as it stands now, or null when it has none
 * @param deliveryLatitude  the delivery point, for a map pin. Null on an order placed
 * @param deliveryLongitude against a typed one-off address, which never had coordinates,
 *                          and on any order placed before these were recorded — so a
 *                          client falls back to {@code shippingAddress} rather than
 *                          assuming a pin is always there
 */
public record PurchaseResponse(
        UUID uuid,
        String buyerId,
        String buyerName,
        String buyerPhone,
        String sellerId,
        String businessName,
        String storeLogoUrl,
        BigDecimal totalPrice,
        PurchaseStatus status,
        PurchaseChannel channel,
        PaymentMethod paymentMethod,
        String shippingAddress,
        BigDecimal deliveryLatitude,
        BigDecimal deliveryLongitude,
        List<DeliveryPhotoResponse> deliveryPhotos,
        String note,
        List<PurchaseItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt
) {
}