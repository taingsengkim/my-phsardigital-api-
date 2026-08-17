package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import co.istad.projectpracticum.phsardigital.features.seller.application.SellerDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddDocumentRequest(
        @NotNull(message = "Document type is required")
        SellerDocumentType docType,

        @NotBlank(message = "Object name is required")
        @Size(max = 512, message = "Object name must not exceed 512 characters")
        String objectName
) {
}
