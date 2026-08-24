package co.istad.projectpracticum.phsardigital.features.pos.dto;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseResponse;

import java.math.BigDecimal;

/**
 * @param changeDue null when no cash amount was given, so the till can tell "no change"
 *                  apart from "change not calculated"
 */
public record PosSaleResponse(
        PurchaseResponse sale,
        BigDecimal amountTendered,
        BigDecimal changeDue
) {
}
