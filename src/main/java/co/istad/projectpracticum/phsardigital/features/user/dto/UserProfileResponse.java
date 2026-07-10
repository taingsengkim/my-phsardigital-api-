package co.istad.projectpracticum.phsardigital.features.user.dto;

import java.time.LocalDate;

public record UserProfileResponse(
        String id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        String status,
        LocalDate dateOfBirth
) {
}