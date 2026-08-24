package co.istad.projectpracticum.phsardigital.features.pos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param unitPrice optional override for a haggled price. Left out, the listing's
 *                  current price is used. It may not exceed the list price — the
 *                  counter can discount, not mark up behind the catalogue's back
 */
public record PosSaleLineRequest(
        @NotNull(message = "Listing UUID is required")
        UUID listingUuid,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        java.math.BigDecimal unitPrice
) {
}
