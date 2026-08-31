package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscribeRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionCheckoutResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /** Open to anyone: a pricing page is read before signing up, not after. */
    @GetMapping("/plans")
    public List<SubscriptionPlanResponse> plans() {
        return subscriptionService.listPlans();
    }

    @GetMapping("/me")
    public SellerSubscriptionResponse mySubscription() {
        return subscriptionService.getMySubscription();
    }

    /**
     * Starts buying a plan, and answers with a KHQR to pay it.
     *
     * <p>Nothing is active when this returns. Render {@code payment.qr} as a QR code,
     * then poll {@code POST /api/v1/payments/{uuid}/verify} until it reports
     * {@code PAID} — that call is what activates the plan. The QR carries its own
     * expiry in {@code payment.expiresAt}, after which this endpoint issues a fresh one.
     *
     * <p>The exception is a plan that costs nothing, which comes back already active
     * with no payment attached; {@code paymentRequired} says which of the two happened.
     */
    @PostMapping("/me")
    public SubscriptionCheckoutResponse subscribe(@Valid @RequestBody SubscribeRequest request) {
        return subscriptionService.subscribe(request);
    }
}
