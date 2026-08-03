package co.istad.projectpracticum.phsardigital.features.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(
                regexp = "^[\\p{L}\\p{N}._-]+$",
                message = "Username may contain only letters, numbers, dots, underscores, and hyphens"
        )
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password,

        @NotBlank(message = "Password confirmation is required")
        @Size(max = 128, message = "Password confirmation must not exceed 128 characters")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String confirmPassword,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\d{9,11}$", message = "Phone number must contain 9 to 11 digits")
        String phoneNumber
) {
}
