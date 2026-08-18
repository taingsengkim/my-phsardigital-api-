package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Where to deliver, one way or the other.
 *
 * @param addressId       a saved address to deliver to. Preferred — it carries a
 *                        recipient and phone number as well as the street
 * @param shippingAddress free-text fallback for a one-off delivery. Exactly one of the
 *                        two must be supplied; checkout used to accept neither, which
 *                        let an order reach a seller with no address on it at all
 */
public record CheckoutRequest(
        UUID addressId,

        @Size(max = 2000, message = "Shipping address must not exceed 2000 characters")
        String shippingAddress,

        @Size(max = 1000, message = "Note must not exceed 1000 characters")
        String note
) {
}
