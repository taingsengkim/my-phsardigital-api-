package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRequest(
        @NotBlank(message = "Rejection note is required")
        String rejectionNote
) {
}