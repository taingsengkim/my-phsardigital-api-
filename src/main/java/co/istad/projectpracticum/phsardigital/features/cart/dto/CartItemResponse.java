package co.istad.projectpracticum.phsardigital.features.cart.dto;

import java.util.UUID;

/**
 * @param fullPrice the listing's list price, above {@code unitPrice} when it is on sale
 * @param unitPrice what this line is charged per unit. Read live off the listing —
 *                  checkout is where the price is frozen
 */
public record CartItemResponse(
        UUID uuid,
        UUID listingUuid,
        String title,
        Double fullPrice,
        Double unitPrice,
        Integer quantity,
        Double lineTotal
) {
}
