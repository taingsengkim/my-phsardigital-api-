package co.istad.projectpracticum.phsardigital.features.purchases.dto;

/**
 * The shop an order was placed with.
 *
 * <p>Deliberately carries no payout or bank details. The platform never handles a shop's
 * takings — {@code SellerProfile#bakongAccountId} explains why — so there is no payout
 * account for an administrator to inspect here, and the shop's Bakong handle is a
 * payment credential rather than order metadata.
 *
 * @param isActive whether the shop may currently trade, so the admin table can mark an
 *                 order whose seller has since been suspended
 */
public record AdminPurchaseSellerResponse(
        String sellerId,
        String businessName,
        String phone,
        String logoUrl,
        Boolean isActive
) {
}
