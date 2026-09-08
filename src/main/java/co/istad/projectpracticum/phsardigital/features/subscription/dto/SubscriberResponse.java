package co.istad.projectpracticum.phsardigital.features.subscription.dto;

/**
 * Who a subscription belongs to, in the terms an admin needs to recognise the row.
 *
 * <p>A subscription is keyed by the seller's Keycloak subject, which identifies a shop
 * to the database and to nobody else. This block is what turns a page of UUIDs into a
 * list somebody can read, and it is filled from the page's own query rather than costing
 * a {@code GET /api/v1/sellers/{id}} call per row.
 *
 * <p>Carries {@code isActive} — the shop's own suspension flag, not the subscription's
 * status — because a live subscription against a suspended shop is exactly the mismatch
 * this screen exists to surface, and the public shop block does not report it.
 */
public record SubscriberResponse(
        String sellerId,
        String businessName,
        String logoUri,
        String phoneNumber,
        String city,
        Boolean isActive
) {
}
