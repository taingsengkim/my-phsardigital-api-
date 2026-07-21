package co.istad.projectpracticum.phsardigital.features.messaging.dto;


import jakarta.validation.constraints.NotBlank;

public record StartConversationRequest(
        @NotBlank String participantId
) {
}