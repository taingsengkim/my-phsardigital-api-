package co.istad.projectpracticum.phsardigital.features.categories.dto;

import java.util.UUID;

public record CategorySummaryResponse(
        UUID uuid,
        String name,
        String slug
) {
}