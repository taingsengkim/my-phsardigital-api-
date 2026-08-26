package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A plan an admin is creating.
 *
 * @param code         uppercase identifier, fixed once created — subscriptions store
 *                     it, so renaming one would orphan everybody on that plan
 * @param listingLimit {@code -1} for unlimited, otherwise the cap
 */
public record SubscriptionPlanRequest(
        @NotBlank(message = "Code is required")
        @Size(max = 30, message = "Code must not exceed 30 characters")
        @Pattern(regexp = "[A-Z][A-Z0-9_]*",
                message = "Code must be uppercase letters, digits and underscores")
        String code,

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name must not exceed 100 characters")
        String displayName,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most two decimals")
        BigDecimal priceUsd,

        @Min(value = 1, message = "Duration must be at least one day")
        int durationDays,

        @Min(value = SubscriptionPlan.UNLIMITED_LISTINGS,
                message = "Listing limit must be -1 for unlimited, or zero or more")
        int listingLimit,

        @Min(value = 0, message = "Sort order cannot be negative")
        int sortOrder
) {
}
