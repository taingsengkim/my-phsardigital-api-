package co.istad.projectpracticum.phsardigital.features.user.dto;

import co.istad.projectpracticum.phsardigital.features.user.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminUserResponse(
        String id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        UserStatus status,
        LocalDate dateOfBirth,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {
}
