package co.istad.projectpracticum.phsardigital.features.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param listingUuid listing whose line should be created or replaced
 * @param quantity amount to add through POST, or desired total through the retry-safe
 *                 PUT endpoint, where zero removes the line. POST rejects zero, since
 *                 adding nothing is a mistake rather than an instruction
 */
public record AddCartItemRequest(
        @NotNull UUID listingUuid,
        @NotNull @Min(0) Integer quantity
) {
}
