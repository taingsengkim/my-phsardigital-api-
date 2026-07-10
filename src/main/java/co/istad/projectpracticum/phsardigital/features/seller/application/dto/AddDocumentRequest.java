package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import jakarta.validation.constraints.NotBlank;

public record AddDocumentRequest(
        @NotBlank String docType,
        @NotBlank String objectName
) {
}