package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import java.math.BigDecimal;

/**
 * Who placed an order, as an administrator sees it.
 *
 * <p>Every field is nullable and that is ordinary rather than exceptional: a counter sale
 * rung up at a seller's till has no signed-in shopper at all, so the whole block is null
 * on those rows. An admin table must render that as "walk-in customer", never as a
 * missing record.
 *
 * @param name          the recipient named on the order, falling back to the account
 *                      holder — the same precedence {@code PurchaseMapper} uses, because
 *                      people order to a parent's or a colleague's address
 * @param lifetimeOrders how many completed orders this buyer has ever placed, or null on
 *                       the list route, which does not compute it — one aggregate per row
 *                       is what turns a page of orders into a hundred queries. Filled in
 *                       on the detail route only.
 * @param lifetimeSpend  what those orders were worth, disclosed on the same terms
 */
public record AdminPurchaseBuyerResponse(
        String id,
        String name,
        String phone,
        String email,
        String avatarUrl,
        Long lifetimeOrders,
        BigDecimal lifetimeSpend
) {

    /** The row view: identity only, no aggregates. */
    public static AdminPurchaseBuyerResponse summary(String id, String name, String phone,
                                                     String email, String avatarUrl) {
        return new AdminPurchaseBuyerResponse(id, name, phone, email, avatarUrl, null, null);
    }
}
