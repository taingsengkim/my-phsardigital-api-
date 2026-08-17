package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscribeRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    /**
     * Archived listings do not count against a plan, so a seller can retire old
     * stock to make room instead of being forced to upgrade or delete history.
     */
    private static final ListingStatus UNCOUNTED_STATUS = ListingStatus.ARCHIVED;

    private final SellerSubscriptionRepository subscriptionRepository;
    private final ListingRepository listingRepository;
    private final SellerAccessGuard sellerAccessGuard;

    @Override
    public List<SubscriptionPlanResponse> listPlans() {
        return Arrays.stream(SubscriptionPlan.values())
                .map(plan -> new SubscriptionPlanResponse(
                        plan,
                        plan.getDisplayName(),
                        plan.getPriceUsd(),
                        plan.getDurationDays(),
                        plan.hasUnlimitedListings() ? null : plan.getListingLimit()))
                .toList();
    }

    @Override
    @Transactional
    public SellerSubscriptionResponse getMySubscription() {
        String sellerId = AuthUtils.extractUserId();
        SellerSubscription subscription = subscriptionRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "You do not have a subscription yet. See GET /api/v1/subscriptions/plans."));

        return toResponse(settleExpiry(subscription));
    }

    @Override
    @Transactional
    public SellerSubscriptionResponse subscribe(SubscribeRequest request) {
        String sellerId = AuthUtils.extractUserId();
        // A suspended shop must not be able to buy its way back in.
        sellerAccessGuard.requireActiveSeller(sellerId);

        SellerSubscription subscription = subscriptionRepository.findById(sellerId)
                .orElseGet(() -> new SellerSubscription(sellerId));

        LocalDateTime now = LocalDateTime.now();
        // Extending from the current expiry rather than from now, so changing plan
        // mid-period does not silently forfeit the days already paid for.
        LocalDateTime base = subscription.isCurrentlyActive() ? subscription.getExpiresAt() : now;

        subscription.setPlan(request.plan());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(subscription.getStartedAt() == null ? now : subscription.getStartedAt());
        subscription.setExpiresAt(base.plusDays(request.plan().getDurationDays()));

        return toResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public void requirePostingAllowed(String sellerId) {
        SellerSubscription subscription = requireActiveSubscription(sellerId,
                "A subscription is required to publish listings.");

        long used = countListings(sellerId);
        if (!subscription.getPlan().allowsAnotherListing(used)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your " + subscription.getPlan().getDisplayName() + " plan allows "
                            + subscription.getPlan().getListingLimit() + " listings and you have "
                            + used + ". Archive a listing or move to a larger plan.");
        }
    }

    @Override
    @Transactional
    public void requireChatAllowed(String sellerId) {
        requireActiveSubscription(sellerId,
                "A subscription is required to message customers.");
    }

    /**
     * Loads the seller's subscription and insists it is currently good.
     *
     * <p>Answers {@code 402 Payment Required} rather than {@code 403}: the caller
     * does hold the seller role and is not doing anything forbidden, they simply have
     * not paid. The distinct status lets a client show the pricing page instead of an
     * error, without string-matching the message.
     */
    private SellerSubscription requireActiveSubscription(String sellerId, String what) {
        Optional<SellerSubscription> found = subscriptionRepository.findById(sellerId);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, what);
        }

        SellerSubscription subscription = settleExpiry(found.get());
        if (!subscription.isCurrentlyActive()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    what + " Yours expired on " + subscription.getExpiresAt() + ".");
        }
        return subscription;
    }

    /**
     * Writes down a lapse the first time anybody notices it. Nothing sweeps the
     * table on a schedule, so without this a row sits at {@code ACTIVE} forever and
     * the stored status disagrees with what the gates actually decided.
     */
    private SellerSubscription settleExpiry(SellerSubscription subscription) {
        boolean lapsed = subscription.getStatus() == SubscriptionStatus.ACTIVE
                && !subscription.isCurrentlyActive();
        if (lapsed) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            log.info("Subscription for seller {} lapsed at {}",
                    subscription.getSellerId(), subscription.getExpiresAt());
            return subscriptionRepository.save(subscription);
        }
        return subscription;
    }

    private long countListings(String sellerId) {
        return listingRepository.countBySellerProfile_SellerIdAndStatusNot(sellerId, UNCOUNTED_STATUS);
    }

    private SellerSubscriptionResponse toResponse(SellerSubscription subscription) {
        SubscriptionPlan plan = subscription.getPlan();
        long used = countListings(subscription.getSellerId());
        boolean active = subscription.isCurrentlyActive();

        return new SellerSubscriptionResponse(
                subscription.getSellerId(),
                plan,
                plan.getDisplayName(),
                subscription.getStatus(),
                subscription.getStartedAt(),
                subscription.getExpiresAt(),
                used,
                plan.hasUnlimitedListings() ? null : plan.getListingLimit(),
                active && plan.allowsAnotherListing(used),
                active
        );
    }
}
