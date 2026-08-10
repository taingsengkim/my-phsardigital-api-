package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddDocumentRequest(
        @NotBlank(message = "Document type is required")
        @Size(max = 100, message = "Document type must not exceed 100 characters")
        String docType,

        @NotBlank(message = "Object name is required")
        @Size(max = 512, message = "Object name must not exceed 512 characters")
        String objectName
) {
}
