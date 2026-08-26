package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.GrantSubscriptionRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSubscriptionServiceImpl implements AdminSubscriptionService {

    /** Mirrors {@code SubscriptionServiceImpl}: archived listings do not count. */
    private static final ListingStatus UNCOUNTED_STATUS = ListingStatus.ARCHIVED;

    private final SubscriptionPlanRepository planRepository;
    private final SellerSubscriptionRepository subscriptionRepository;
    private final ListingRepository listingRepository;

    // ---- the catalogue -------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> listPlans() {
        return planRepository.findAllByOrderBySortOrderAsc().stream()
                .map(SubscriptionPlanResponse::of)
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (planRepository.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A plan with code '" + code + "' already exists.");
        }

        SubscriptionPlan plan = new SubscriptionPlan(code);
        plan.setDisplayName(request.displayName().trim());
        plan.setPriceUsd(request.priceUsd());
        plan.setDurationDays(request.durationDays());
        plan.setListingLimit(request.listingLimit());
        plan.setSortOrder(request.sortOrder());
        plan.setActive(true);

        log.info("Plan {} created by {}", code, AuthUtils.extractUserId());
        return SubscriptionPlanResponse.of(planRepository.save(plan));
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse updatePlan(String code, SubscriptionPlanUpdateRequest request) {
        SubscriptionPlan plan = requirePlan(code);

        if (request.displayName() != null) {
            plan.setDisplayName(request.displayName().trim());
        }
        if (request.priceUsd() != null) {
            plan.setPriceUsd(request.priceUsd());
        }
        if (request.durationDays() != null) {
            plan.setDurationDays(request.durationDays());
        }
        if (request.listingLimit() != null) {
            plan.setListingLimit(request.listingLimit());
        }
        if (request.sortOrder() != null) {
            plan.setSortOrder(request.sortOrder());
        }

        log.info("Plan {} updated by {}", plan.getCode(), AuthUtils.extractUserId());
        return SubscriptionPlanResponse.of(planRepository.save(plan));
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse deactivatePlan(String code) {
        SubscriptionPlan plan = requirePlan(code);
        if (!plan.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This plan is already retired.");
        }

        plan.setActive(false);
        // Not a refusal: sellers mid-period keep the plan until it runs out. Worth
        // saying out loud, because retiring a plan somebody is on is easy to do by
        // accident and impossible to notice afterwards.
        if (subscriptionRepository.existsByPlanCodeAndStatus(
                plan.getCode(), SubscriptionStatus.ACTIVE)) {
            log.warn("Plan {} retired by {} while sellers are still on it; "
                            + "they keep it until their period ends",
                    plan.getCode(), AuthUtils.extractUserId());
        }
        return SubscriptionPlanResponse.of(planRepository.save(plan));
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse activatePlan(String code) {
        SubscriptionPlan plan = requirePlan(code);
        if (plan.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This plan is already on offer.");
        }

        plan.setActive(true);
        log.info("Plan {} returned to the pricing page by {}",
                plan.getCode(), AuthUtils.extractUserId());
        return SubscriptionPlanResponse.of(planRepository.save(plan));
    }

    // ---- who is on what ------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<SellerSubscriptionResponse> list(SubscriptionStatus status, String planCode,
                                                 int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(
                pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "expiresAt"));

        Page<SellerSubscription> page;
        if (status != null && planCode != null) {
            page = subscriptionRepository.findByStatusAndPlanCode(status, planCode, pageable);
        } else if (status != null) {
            page = subscriptionRepository.findByStatus(status, pageable);
        } else if (planCode != null) {
            page = subscriptionRepository.findByPlanCode(planCode, pageable);
        } else {
            page = subscriptionRepository.findAll(pageable);
        }
        if (page.isEmpty()) {
            return page.map(subscription -> toResponse(subscription, null));
        }

        // One catalogue read for the page rather than one per row.
        Map<String, SubscriptionPlan> plans = new HashMap<>();
        planRepository.findAllById(
                        page.getContent().stream().map(SellerSubscription::getPlanCode).toList())
                .forEach(plan -> plans.put(plan.getCode(), plan));

        return page.map(subscription -> toResponse(subscription, plans.get(subscription.getPlanCode())));
    }

    @Override
    @Transactional
    public SellerSubscriptionResponse grant(String sellerId, GrantSubscriptionRequest request) {
        // Unlike the seller's own subscribe, a retired plan is allowed: an admin
        // putting somebody back on a legacy plan is deliberate.
        SubscriptionPlan plan = planRepository.findById(request.planCode().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown plan '" + request.planCode() + "'."));

        SellerSubscription subscription = subscriptionRepository.findById(sellerId)
                .orElseGet(() -> new SellerSubscription(sellerId));

        LocalDateTime now = LocalDateTime.now();
        boolean extend = request.extendExisting() == null || request.extendExisting();
        LocalDateTime base = extend && subscription.isCurrentlyActive()
                ? subscription.getExpiresAt()
                : now;
        int days = request.days() == null ? plan.getDurationDays() : request.days();

        subscription.setPlanCode(plan.getCode());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(
                subscription.getStartedAt() == null ? now : subscription.getStartedAt());
        subscription.setExpiresAt(base.plusDays(days));

        log.info("Seller {} granted {} for {} days by {}",
                sellerId, plan.getCode(), days, AuthUtils.extractUserId());
        return toResponse(subscriptionRepository.save(subscription), plan);
    }

    @Override
    @Transactional
    public SellerSubscriptionResponse cancel(String sellerId) {
        SellerSubscription subscription = subscriptionRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This seller has no subscription."));
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This subscription is already cancelled.");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        // Pulled back to now so the gates, which judge against the clock as well as the
        // stored status, stop allowing posting immediately rather than at the old date.
        subscription.setExpiresAt(LocalDateTime.now());

        log.info("Subscription for seller {} cancelled by {}",
                sellerId, AuthUtils.extractUserId());
        return toResponse(subscription, null);
    }

    private SubscriptionPlan requirePlan(String code) {
        return planRepository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plan '" + code + "' not found"));
    }

    /**
     * @param plan the subscription's plan, or null when it was not loaded — a
     *             cancelled row's plan details are not what the caller is asking about
     */
    private SellerSubscriptionResponse toResponse(SellerSubscription subscription,
                                                  SubscriptionPlan plan) {
        long used = listingRepository.countBySellerProfile_SellerIdAndStatusNot(
                subscription.getSellerId(), UNCOUNTED_STATUS);
        boolean active = subscription.isCurrentlyActive();

        return new SellerSubscriptionResponse(
                subscription.getSellerId(),
                subscription.getPlanCode(),
                plan == null ? subscription.getPlanCode() : plan.getDisplayName(),
                subscription.getStatus(),
                subscription.getStartedAt(),
                subscription.getExpiresAt(),
                used,
                plan == null ? null : plan.listingLimitOrNull(),
                active && plan != null && plan.allowsAnotherListing(used),
                active);
    }
}
