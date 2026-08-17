package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

public record SubscribeRequest(
        @NotNull(message = "Plan is required")
        SubscriptionPlan plan
) {
}
