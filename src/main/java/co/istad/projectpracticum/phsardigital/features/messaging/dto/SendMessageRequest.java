package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank(message = "Message cannot be empty")
        String body
) {
}