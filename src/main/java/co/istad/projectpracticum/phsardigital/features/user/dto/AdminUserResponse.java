package co.istad.projectpracticum.phsardigital.features.user.dto;

import co.istad.projectpracticum.phsardigital.features.user.Gender;
import co.istad.projectpracticum.phsardigital.features.user.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminUserResponse(
        String id,
        String username,
        String email,
        Boolean emailVerified,
        String fullName,
        String phone,
        String avatarUrl,
        Gender gender,
        UserStatus status,
        LocalDate dateOfBirth,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {
}
