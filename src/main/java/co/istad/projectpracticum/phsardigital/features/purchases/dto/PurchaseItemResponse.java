package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param fullPrice the list price at the time of the order, or null on orders placed
 *                  before this was recorded
 * @param unitPrice what was charged per unit, frozen at checkout
 */
public record PurchaseItemResponse(
        UUID listingUuid,
        String title,
        Integer quantity,
        BigDecimal fullPrice,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
