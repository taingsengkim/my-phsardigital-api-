package co.istad.projectpracticum.phsardigital.features.review.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ReviewReplyRequest(
        @NotBlank String comment,
        UUID parentReplyUuid
) {
}
