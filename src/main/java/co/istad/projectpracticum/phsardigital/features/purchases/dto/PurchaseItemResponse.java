package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param fullPrice    the list price at the time of the order, or null on orders placed
 *                     before this was recorded
 * @param unitPrice    what was charged per unit, frozen at checkout
 * @param slug         the listing's current slug, for linking back to the product page.
 *                     Read live rather than frozen, unlike the prices: a slug is a route
 *                     to a page that still exists, not a record of what was bought, so
 *                     an old one would only produce a dead link.
 * @param thumbnailUrl the product photo as it stands now, or null when the listing never
 *                     had one or the upload behind it has since been deleted
 */
public record PurchaseItemResponse(
        UUID listingUuid,
        String title,
        String slug,
        String thumbnailUrl,
        Integer quantity,
        BigDecimal fullPrice,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
