package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import co.istad.projectpracticum.phsardigital.features.purchases.PaymentMethod;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseChannel;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the admin orders table.
 *
 * <p>There is no commission or payout figure here, and that is not an omission: this
 * marketplace earns from seller subscriptions and takes no cut of an order, so
 * {@code totalPrice} is what the buyer pays the shop and the platform's share of it is
 * nothing. Inventing a split would put a number in an admin's financial view that
 * corresponds to no money anywhere.
 *
 * @param paymentMethod null on an online order, and deliberately so — those settle in
 *                      cash on delivery through a courier this system never hears from,
 *                      so nobody has confirmed how it was paid. Render it as "unconfirmed",
 *                      not as a blank cell.
 * @param buyer         null on a counter sale, which has no signed-in shopper
 */
public record AdminPurchaseRowResponse(
        UUID uuid,
        LocalDateTime createdAt,
        PurchaseStatus status,
        PurchaseChannel channel,
        PaymentMethod paymentMethod,
        BigDecimal totalPrice,
        AdminPurchaseBuyerResponse buyer,
        AdminPurchaseSellerResponse seller,
        AdminPurchaseItemSummaryResponse items,
        LocalDateTime confirmedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt
) {
}
