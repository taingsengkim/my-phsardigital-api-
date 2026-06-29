package co.istad.projectpracticum.phsardigital.features.categories.dto;

import java.util.List;
import java.util.UUID;

public record CategoryTreeResponse(
        UUID uuid,
        String name,
        String slug,
        String iconUrl,
        String description,
        Integer level,
        List<CategoryTreeResponse> children
) {
}