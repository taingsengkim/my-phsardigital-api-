package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import co.istad.projectpracticum.phsardigital.features.seller.application.SellerDocumentType;

import java.util.UUID;

public record ApplicationDocumentResponse(
        UUID uuid,
        SellerDocumentType docType,
        String objectName,
        String uri
) {}
