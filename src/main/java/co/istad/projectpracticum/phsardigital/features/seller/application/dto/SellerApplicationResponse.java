package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import co.istad.projectpracticum.phsardigital.features.seller.application.ApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SellerApplicationResponse(
        UUID uuid,
        String applicantId,
        String businessName,
        String businessType,
        String description,
        String city,
        String province,
        ApplicationStatus status,
        String rejectionNote,
        LocalDateTime reviewedAt,
        List<ApplicationDocumentResponse> documents,
        LocalDateTime createdAt
) {
}
