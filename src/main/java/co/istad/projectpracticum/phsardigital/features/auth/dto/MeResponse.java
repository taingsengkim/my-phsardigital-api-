package co.istad.projectpracticum.phsardigital.features.auth.dto;
import lombok.Builder;

import java.util.List;

@Builder
public record MeResponse(
        String userId,
        String username,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        List<String> roles,
        boolean isSeller,
        String sellerId
) {
}