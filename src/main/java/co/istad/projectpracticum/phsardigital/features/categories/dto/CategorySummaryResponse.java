package co.istad.projectpracticum.phsardigital.features.categories.dto;

import java.util.UUID;

public record CategorySummaryResponse(
        String name,
        String slug
) {
}