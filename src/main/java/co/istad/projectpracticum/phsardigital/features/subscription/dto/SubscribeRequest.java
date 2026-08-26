package co.istad.projectpracticum.phsardigital.features.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param planCode the plan's code from {@code GET /api/v1/subscriptions/plans}. A
 *                 string rather than an enum now that the catalogue is a table an
 *                 admin edits — an unknown or retired code is refused by the service,
 *                 not by deserialization.
 */
public record SubscribeRequest(
        @NotBlank(message = "Plan is required")
        @Size(max = 30, message = "Plan code must not exceed 30 characters")
        String planCode
) {
}
