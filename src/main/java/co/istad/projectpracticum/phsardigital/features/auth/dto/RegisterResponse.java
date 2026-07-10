package co.istad.projectpracticum.phsardigital.features.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
public record RegisterResponse(
        String userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber
) {
}
