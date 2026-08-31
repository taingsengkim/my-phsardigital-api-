package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import co.istad.projectpracticum.phsardigital.features.payments.dto.PaymentResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;

import java.math.BigDecimal;

/**
 * What comes back from asking to subscribe.
 *
 * <p>Two shapes, told apart by {@link #paymentRequired}. Ordinarily the plan costs
 * money, so this carries a {@link #payment} holding a KHQR to scan and nothing is
 * granted yet — the client renders the QR and polls
 * {@code POST /api/v1/payments/{uuid}/verify} until it reports {@code PAID}. A plan
 * priced at zero has nothing to collect, so it is granted immediately and
 * {@link #subscription} is filled in instead.
 *
 * @param payment      the QR to pay, or null when the plan was free
 * @param subscription the active subscription, or null until the payment settles
 */
public record SubscriptionCheckoutResponse(
        String planCode,
        String planDisplayName,
        BigDecimal priceUsd,
        int durationDays,
        boolean paymentRequired,
        PaymentResponse payment,
        SellerSubscriptionResponse subscription
) {

    /** A plan that costs money: here is the QR, nothing is active yet. */
    public static SubscriptionCheckoutResponse awaitingPayment(SubscriptionPlan plan,
                                                               PaymentResponse payment) {
        return new SubscriptionCheckoutResponse(plan.getCode(), plan.getDisplayName(),
                plan.getPriceUsd(), plan.getDurationDays(), true, payment, null);
    }

    /** A free plan: nothing to collect, so it is already active. */
    public static SubscriptionCheckoutResponse activated(SubscriptionPlan plan,
                                                         SellerSubscriptionResponse subscription) {
        return new SubscriptionCheckoutResponse(plan.getCode(), plan.getDisplayName(),
                plan.getPriceUsd(), plan.getDurationDays(), false, null, subscription);
    }
}
