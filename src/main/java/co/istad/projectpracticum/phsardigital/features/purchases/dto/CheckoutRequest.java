package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Where to deliver, one way or the other.
 *
 * @param cartUuid        the cart shown to the buyer; it must also be sent as the
 *                        {@code Idempotency-Key} header
 * @param addressId       a saved address to deliver to. Preferred — it carries a
 *                        recipient and phone number as well as the street
 * @param shippingAddress free-text fallback for a one-off delivery. Exactly one of the
 *                        two must be supplied; checkout used to accept neither, which
 *                        let an order reach a seller with no address on it at all
 * @param recipientName   optional per-order override; otherwise the saved address or
 *                        buyer profile supplies it
 * @param recipientPhone  optional per-order override; otherwise the saved address or
 *                        buyer profile supplies it
 * @param note             optional delivery instructions
 */
public record CheckoutRequest(
        @NotNull(message = "Cart UUID is required")
        UUID cartUuid,

        UUID addressId,

        @Size(max = 2000, message = "Shipping address must not exceed 2000 characters")
        String shippingAddress,

        @Size(max = 255, message = "Recipient name must not exceed 255 characters")
        String recipientName,

        @Size(max = 30, message = "Recipient phone must not exceed 30 characters")
        String recipientPhone,

        @Size(max = 1000, message = "Note must not exceed 1000 characters")
        String note
) {

    /** Convenience constructor for callers using a saved contact or profile fallback. */
    public CheckoutRequest(UUID cartUuid,
                           UUID addressId,
                           String shippingAddress,
                           String note) {
        this(cartUuid, addressId, shippingAddress, null, null, note);
    }
}
