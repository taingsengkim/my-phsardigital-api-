package co.istad.projectpracticum.phsardigital.features.seller.application.dto;

import co.istad.projectpracticum.phsardigital.features.seller.application.ApplicationStatus;
import co.istad.projectpracticum.phsardigital.features.seller.application.SellerDocumentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SellerApplicationResponse(
        UUID uuid,
        String applicantId,
        String businessName,
        String businessType,
        String description,
        String logoObjectName,
        String logoUri,
        String address,
        String city,
        String province,
        BigDecimal latitude,
        BigDecimal longitude,
        String googleMapUrl,
        ApplicationStatus status,
        String rejectionNote,
        LocalDateTime reviewedAt,
        List<ApplicationDocumentResponse> documents,
        /**
         * Which required documents are still missing. Answered on the applicant's own
         * view so they can see why review has not started, and on the admin list so a
         * reviewer does not have to cross-check the document array by eye.
         */
        List<SellerDocumentType> missingDocuments,
        LocalDateTime createdAt
) {
}
