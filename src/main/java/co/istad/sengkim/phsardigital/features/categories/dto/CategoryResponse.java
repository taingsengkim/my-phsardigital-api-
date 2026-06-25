package co.istad.sengkim.phsardigital.features.categories.dto;


import lombok.Getter;
import lombok.Setter;

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
