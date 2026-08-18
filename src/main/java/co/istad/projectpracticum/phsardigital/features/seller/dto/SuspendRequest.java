package co.istad.projectpracticum.phsardigital.features.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why something was taken down. Required rather than optional: a suspension with no
 * stated reason cannot be explained to the seller who asks about it, or defended by
 * the next admin who inherits it.
 */
public record SuspendRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason
) {
}
