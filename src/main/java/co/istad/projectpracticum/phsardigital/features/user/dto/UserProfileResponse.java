package co.istad.projectpracticum.phsardigital.features.user.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
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