package co.istad.projectpracticum.phsardigital.features.user.dto;

import co.istad.projectpracticum.phsardigital.features.user.Gender;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record UserProfileResponse(
        String id,
        String username,
        String email,
        Boolean emailVerified,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        String avatarUrl,
        String avatarObjectName,
        Gender gender,
        String bio,
        LocalDate dateOfBirth,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {
}
