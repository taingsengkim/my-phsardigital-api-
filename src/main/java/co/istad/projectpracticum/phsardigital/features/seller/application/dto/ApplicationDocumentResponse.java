package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import java.util.UUID;

public record ApplicationDocumentResponse(
        UUID uuid,
        String docType,
        String objectName,
        String uri
) {}