package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscribeRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanResponse;

import java.util.List;

/**
 * Decides whether a seller has paid for the things a subscription buys: publishing
 * listings, and replying to customers.
 *
 * <p>The plan catalogue is static (see {@link SubscriptionPlan}) and subscribing
 * takes no payment, so today any approved seller can grant themselves any plan.
 * The checks are real regardless, which is the point: when billing arrives it
 * replaces {@link #subscribe} only, and every gate keeps working untouched.
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
     * Puts the caller on a plan, starting now. Re-subscribing while still inside a
     * period extends from the existing expiry rather than from today, so switching
     * plan never throws away time already paid for.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404/403 when the
     *         caller is not an active seller
     */
    SellerSubscriptionResponse subscribe(SubscribeRequest request);

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
