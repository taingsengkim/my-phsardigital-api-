package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Edits to an existing plan. Every field is optional; a null one is left as it was.
 *
 * <p>{@code code} is absent on purpose — it is the plan's identity and subscriptions
 * store it.
 *
 * <p>Changes apply to what a plan <em>allows</em> from now on, not retroactively to
 * what anyone paid: an existing subscription keeps its expiry, so shortening
 * {@code durationDays} does not cut a current period short. Lowering
 * {@code listingLimit} below what a seller already has does not delete listings — it
 * stops them adding more until they are back under the cap.
 */
public record SubscriptionPlanUpdateRequest(
        @Size(max = 100, message = "Display name must not exceed 100 characters")
        String displayName,

        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most two decimals")
        BigDecimal priceUsd,

        @Min(value = 1, message = "Duration must be at least one day")
        Integer durationDays,

        @Min(value = SubscriptionPlan.UNLIMITED_LISTINGS,
                message = "Listing limit must be -1 for unlimited, or zero or more")
        Integer listingLimit,

        @Min(value = 0, message = "Sort order cannot be negative")
        Integer sortOrder
) {
}
