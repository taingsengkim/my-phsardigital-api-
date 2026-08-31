package co.istad.projectpracticum.phsardigital.features.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Puts a seller on a plan.
 *
 * <p>Its own bean, and deliberately unaware of how the plan was paid for, because two
 * different things now grant a subscription on the plan's own terms: a settled KHQR
 * payment, and a plan that costs nothing. {@code AdminSubscriptionService#grant} keeps
 * its own copy of this rule, since an admin may override the number of days and choose
 * not to extend, and folding those options in here would complicate the common path for
 * the sake of the rare one.
 *
 * <p>This also breaks what would otherwise be a dependency cycle. Subscribing starts a
 * payment, and settling a payment activates a subscription; if the service that did the
 * first also did the second, {@code payments} and {@code subscription} would each need
 * the other. Nothing here knows payments exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionActivation {

    private final SellerSubscriptionRepository subscriptionRepository;

    /**
     * Grants {@code plan} to {@code sellerId} for the plan's own duration.
     *
     * <p>Extends from the current expiry rather than from today whenever the seller is
     * still inside a period, so changing plan mid-month does not silently forfeit the
     * days already paid for.
     */
    @Transactional
    public SellerSubscription activate(String sellerId, SubscriptionPlan plan) {
        SellerSubscription subscription = subscriptionRepository.findById(sellerId)
                .orElseGet(() -> new SellerSubscription(sellerId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = subscription.isCurrentlyActive() ? subscription.getExpiresAt() : now;

        subscription.setPlanCode(plan.getCode());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(
                subscription.getStartedAt() == null ? now : subscription.getStartedAt());
        subscription.setExpiresAt(base.plusDays(plan.getDurationDays()));

        log.info("Seller {} activated on {} until {}",
                sellerId, plan.getCode(), subscription.getExpiresAt());
        return subscriptionRepository.save(subscription);
    }
}
