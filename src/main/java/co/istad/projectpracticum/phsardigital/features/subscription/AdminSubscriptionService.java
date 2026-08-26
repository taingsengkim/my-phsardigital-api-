package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.features.subscription.dto.GrantSubscriptionRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanUpdateRequest;
import org.springframework.data.domain.Page;

import java.util.List;

/** Admin control over the plan catalogue and over who is on what. */
public interface AdminSubscriptionService {

    // ---- the catalogue -------------------------------------------------

    /** Every plan, retired ones included, in pricing-page order. */
    List<SubscriptionPlanResponse> listPlans();

    SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request);

    /** Applies only the fields present on the request. */
    SubscriptionPlanResponse updatePlan(String code, SubscriptionPlanUpdateRequest request);

    /**
     * Takes a plan off the pricing page. Sellers already on it keep it until their
     * period ends — plans are never deleted, because subscriptions name them.
     */
    SubscriptionPlanResponse deactivatePlan(String code);

    SubscriptionPlanResponse activatePlan(String code);

    // ---- who is on what ------------------------------------------------

    /**
     * @param status   filters by standing; null lists every subscription
     * @param planCode filters by plan; null lists every plan's
     */
    Page<SellerSubscriptionResponse> list(SubscriptionStatus status, String planCode,
                                          int pageNumber, int pageSize);

    /** Puts a seller on a plan, or extends the one they already have. */
    SellerSubscriptionResponse grant(String sellerId, GrantSubscriptionRequest request);

    /** Ends a subscription now, without waiting for its period to run out. */
    SellerSubscriptionResponse cancel(String sellerId);
}
