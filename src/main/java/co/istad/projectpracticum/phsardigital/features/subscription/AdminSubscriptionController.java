package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.features.subscription.dto.GrantSubscriptionRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin subscription screens: the plan catalogue, and who is on what.
 *
 * <p>Plans have no delete. A subscription names its plan by code, so removing a row
 * would orphan everybody who ever held it; {@code deactivate} takes it off the pricing
 * page instead and leaves current subscribers alone.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Validated
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    // ---- the catalogue -------------------------------------------------

    /** Every plan, retired ones included. The public pricing page shows only active ones. */
    @GetMapping("/subscription-plans")
    public List<SubscriptionPlanResponse> listPlans() {
        return adminSubscriptionService.listPlans();
    }

    @PostMapping("/subscription-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionPlanResponse createPlan(@Valid @RequestBody SubscriptionPlanRequest request) {
        return adminSubscriptionService.createPlan(request);
    }

    /** Only the fields present on the body are applied. */
    @PatchMapping("/subscription-plans/{code}")
    public SubscriptionPlanResponse updatePlan(
            @PathVariable String code,
            @Valid @RequestBody SubscriptionPlanUpdateRequest request) {
        return adminSubscriptionService.updatePlan(code, request);
    }

    @PatchMapping("/subscription-plans/{code}/deactivate")
    public SubscriptionPlanResponse deactivatePlan(@PathVariable String code) {
        return adminSubscriptionService.deactivatePlan(code);
    }

    @PatchMapping("/subscription-plans/{code}/activate")
    public SubscriptionPlanResponse activatePlan(@PathVariable String code) {
        return adminSubscriptionService.activatePlan(code);
    }

    // ---- who is on what ------------------------------------------------

    @GetMapping("/subscriptions")
    public Page<SellerSubscriptionResponse> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) String planCode,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize) {
        return adminSubscriptionService.list(status, planCode, pageNumber, pageSize);
    }

    @PatchMapping("/subscriptions/{sellerId}/grant")
    public SellerSubscriptionResponse grant(@PathVariable String sellerId,
                                            @Valid @RequestBody GrantSubscriptionRequest request) {
        return adminSubscriptionService.grant(sellerId, request);
    }

    @PatchMapping("/subscriptions/{sellerId}/cancel")
    public SellerSubscriptionResponse cancel(@PathVariable String sellerId) {
        return adminSubscriptionService.cancel(sellerId);
    }
}
