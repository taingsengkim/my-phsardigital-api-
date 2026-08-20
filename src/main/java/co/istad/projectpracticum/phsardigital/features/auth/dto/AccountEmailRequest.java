package co.istad.projectpracticum.phsardigital.features.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Names an account by its email address, for the two endpoints a user reaches when
 * they cannot get in: resending the verification mail and starting a password reset.
 */
public record AccountEmailRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email
) {
}
