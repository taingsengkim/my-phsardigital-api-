package co.istad.projectpracticum.phsardigital.features.cart.dto;

import java.util.UUID;

/**
 * @param thumbnailUri the listing's thumbnail, so a cart line draws without a lookup per
 *                     row back to the product
 * @param fullPrice    the listing's list price, above {@code unitPrice} when it is on sale
 * @param unitPrice    what this line is charged per unit. Read live off the listing —
 *                     checkout is where the price is frozen
 */
public record CartItemResponse(
        UUID uuid,
        UUID listingUuid,
        String title,
        String thumbnailUri,
        Double fullPrice,
        Double unitPrice,
        Integer quantity,
        Double lineTotal
) {
}
