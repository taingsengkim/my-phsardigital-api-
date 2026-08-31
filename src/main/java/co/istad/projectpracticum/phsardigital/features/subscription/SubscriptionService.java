package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscribeRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionCheckoutResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanResponse;

import java.util.List;

/**
 * Decides whether a seller has paid for the things a subscription buys: publishing
 * listings, and replying to customers.
 *
 * <p>Money is collected over Bakong KHQR. {@link #subscribe} no longer grants anything
 * — it issues a QR and returns, and the plan begins only once Bakong confirms the
 * transfer. The gates below were written before billing existed and did not change when
 * it arrived, which was the point of keeping them honest while nothing enforced them.
 *
 * <p>Two paths still bypass payment, both deliberately: a plan priced at zero, and an
 * admin granting one through {@code AdminSubscriptionService#grant}, which is how a
 * comped or manually-settled subscription is recorded.
 */
public interface SubscriptionService {

    /** The plan catalogue, for a pricing page. */
    List<SubscriptionPlanResponse> listPlans();

    /**
     * @return the caller's subscription
     * @throws org.springframework.web.server.ResponseStatusException 404 when they
     *         have never subscribed
     */
    SellerSubscriptionResponse getMySubscription();

    /**
     * Starts buying a plan: returns a KHQR for the caller to pay.
     *
     * <p>Grants nothing by itself. The client renders the returned QR and polls
     * {@code POST /api/v1/payments/{uuid}/verify}; the plan activates there. Pressing
     * this twice returns the same QR rather than minting a second one.
     *
     * <p>Once it does activate, a subscription bought while an existing one is still
     * running extends from that expiry rather than from the day it settles, so
     * switching plan mid-period never throws away time already paid for.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 for an unknown
     *         plan, 409 for a retired one, 404/403 when the caller is not an active
     *         seller, 503 when this server has no Bakong credentials configured
     */
    SubscriptionCheckoutResponse subscribe(SubscribeRequest request);

    /**
     * Asserts the seller may publish one more listing.
     *
     * @throws org.springframework.web.server.ResponseStatusException 402 when there
     *         is no subscription or it has lapsed, 409 when the plan's listing limit
     *         is already reached
     */
    void requirePostingAllowed(String sellerId);

    /**
     * Asserts the seller may send a message.
     *
     * @throws org.springframework.web.server.ResponseStatusException 402 when there
     *         is no subscription or it has lapsed
     */
    void requireChatAllowed(String sellerId);
}
