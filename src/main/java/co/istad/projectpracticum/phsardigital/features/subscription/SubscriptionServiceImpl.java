package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.payments.Payment;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentCurrency;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentPurpose;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentService;
import co.istad.projectpracticum.phsardigital.features.payments.dto.PaymentResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscribeRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionCheckoutResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    /**
     * Statuses that do not count against a plan.
     *
     * <p>Archived, so a seller can retire old stock to make room instead of being
     * forced to upgrade or delete history. Removed, because a listing an admin took
     * down is not one the seller can free up, and charging them a slot for it forever
     * would turn a moderation decision into a billing one.
     *
     * <p>Deliberately a short explicit list: any status added later counts by default
     * rather than silently slipping past the limit.
     */
    private static final Set<ListingStatus> UNCOUNTED_STATUSES =
            EnumSet.of(ListingStatus.ARCHIVED, ListingStatus.REMOVED);

    private final SellerSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final ListingRepository listingRepository;
    private final SellerAccessGuard sellerAccessGuard;
    private final SubscriptionActivation activation;
    private final PaymentService paymentService;

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> listPlans() {
        return planRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(SubscriptionPlanResponse::of)
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
    public SubscriptionCheckoutResponse subscribe(SubscribeRequest request) {
        String sellerId = AuthUtils.extractUserId();
        // A suspended shop must not be able to buy its way back in.
        sellerAccessGuard.requireActiveSeller(sellerId);

        // The code came from the caller, so an unknown one is their mistake, not a
        // broken catalogue — 404 rather than the 500 requirePlan answers with.
        SubscriptionPlan plan = planRepository.findById(request.planCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown plan '" + request.planCode()
                                + "'. See GET /api/v1/subscriptions/plans."));
        // A retired plan is not on offer, even to somebody who knows its code.
        if (!plan.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The " + plan.getDisplayName() + " plan is no longer offered.");
        }

        BigDecimal price = Money.of(plan.getPriceUsd());
        // A plan priced at nothing has nothing to collect, and sending somebody to a
        // payment app for $0.00 would be absurd — KHQR will not even encode it, since a
        // zero amount makes a static QR rather than a dynamic one.
        if (price == null || price.signum() <= 0) {
            SellerSubscription granted = activation.activate(sellerId, plan);
            return SubscriptionCheckoutResponse.activated(plan, toResponse(granted));
        }

        // Nothing is granted here. The seller gets a QR; the plan starts when
        // PaymentServiceImpl hears from Bakong that the transfer landed, which is what
        // SubscriptionPaymentSettlement does with it.
        Payment payment = paymentService.start(PaymentPurpose.SUBSCRIPTION, plan.getCode(),
                sellerId, price, PaymentCurrency.USD);
        return SubscriptionCheckoutResponse.awaitingPayment(plan, PaymentResponse.of(payment));
    }

    @Override
    @Transactional
    public void requirePostingAllowed(String sellerId) {
        SellerSubscription subscription = requireActiveSubscription(sellerId,
                "A subscription is required to publish listings.");

        SubscriptionPlan plan = requirePlan(subscription.getPlanCode());
        long used = countListings(sellerId);
        if (!plan.allowsAnotherListing(used)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your " + plan.getDisplayName() + " plan allows "
                            + plan.getListingLimit() + " listings and you have "
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
        return listingRepository.countBySellerProfile_SellerIdAndStatusNotIn(
                sellerId, UNCOUNTED_STATUSES);
    }

    /**
     * A subscription names its plan by code. The catalogue never deletes a plan, so a
     * code with no row behind it means the table was tampered with rather than that
     * the seller did anything wrong — hence 500, not 404 or 402.
     */
    private SubscriptionPlan requirePlan(String planCode) {
        return planRepository.findById(planCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Subscription plan '" + planCode + "' is missing from the catalogue."));
    }

    private SellerSubscriptionResponse toResponse(SellerSubscription subscription) {
        SubscriptionPlan plan = requirePlan(subscription.getPlanCode());
        long used = countListings(subscription.getSellerId());
        boolean active = subscription.isCurrentlyActive();

        return new SellerSubscriptionResponse(
                subscription.getSellerId(),
                plan.getCode(),
                plan.getDisplayName(),
                subscription.getStatus(),
                subscription.getStartedAt(),
                subscription.getExpiresAt(),
                used,
                plan.listingLimitOrNull(),
                active && plan.allowsAnotherListing(used),
                active
        );
    }
}
