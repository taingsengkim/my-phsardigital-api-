package co.istad.projectpracticum.phsardigital.features.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why an account was suspended or banned. Required for the same reason a shop's
 * suspension reason is: a moderation act nobody wrote a reason for cannot be explained
 * to the person it happened to, or defended by the next admin who inherits it.
 */
public record ModerateBuyerRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason
) {
}
