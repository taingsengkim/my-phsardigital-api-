package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import java.util.List;

/**
 * Everything the inspection drawer shows about one order.
 *
 * <p>Composed around {@link PurchaseResponse} rather than restating its twenty fields,
 * so the order an administrator reads is byte-for-byte the order its buyer and seller
 * read. A parallel admin-shaped copy of the same data drifts the first time someone adds
 * a field to one of them.
 *
 * @param order      the order itself: items, delivery address and pin, landmark photos,
 *                   note, and the four lifecycle timestamps
 * @param buyer      identity plus lifetime totals, which the list route leaves null
 * @param activities the lifecycle timeline, oldest first
 */
public record AdminPurchaseDetailResponse(
        PurchaseResponse order,
        AdminPurchaseBuyerResponse buyer,
        AdminPurchaseSellerResponse seller,
        List<PurchaseActivityResponse> activities
) {
}
