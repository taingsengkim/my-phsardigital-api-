package co.istad.projectpracticum.phsardigital.features.categories.dto;


import java.util.UUID;

public record CategoryResponse(
    UUID uuid,
    String name,
    String slug,
    String iconUrl,
    String description,
    Integer level,
    Integer sortOrder,
    Boolean isActive,
    UUID parentUuid
) {
}
