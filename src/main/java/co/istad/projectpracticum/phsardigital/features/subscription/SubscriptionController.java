package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.features.subscription.dto.SellerSubscriptionResponse;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscribeRequest;
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
     * Activates a plan for the calling seller. Takes no payment — see
     * {@link SubscriptionService} for what that means today.
     */
    @PostMapping("/me")
    public SellerSubscriptionResponse subscribe(@Valid @RequestBody SubscribeRequest request) {
        return subscriptionService.subscribe(request);
    }
}
