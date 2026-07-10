package co.istad.projectpracticum.phsardigital.features.user.dto;

import java.time.LocalDate;

public record UpdateUserProfileRequest(
        String firstName,
        String lastName,
        String phone,
        String avatarUrl,
        LocalDate dateOfBirth
) {
}