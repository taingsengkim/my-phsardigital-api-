package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An admin putting a seller on a plan.
 *
 * @param planCode     the plan to grant. A retired plan is allowed here, unlike on the
 *                     seller's own {@code subscribe} — an admin restoring somebody to
 *                     a legacy plan is a deliberate act, not an accident
 * @param days         how long to grant, or null to use the plan's own duration
 * @param extendExisting whether to add the time onto an unexpired period rather than
 *                       restart it from today. Null counts as true, which is what
 *                       {@code subscribe} does and what a seller expects
 */
public record GrantSubscriptionRequest(
        @NotBlank(message = "Plan is required")
        @Size(max = 30, message = "Plan code must not exceed 30 characters")
        String planCode,

        @Min(value = 1, message = "Days must be at least 1")
        @Max(value = 3650, message = "Days must not exceed 3650")
        Integer days,

        Boolean extendExisting
) {
}
