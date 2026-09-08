package co.istad.projectpracticum.phsardigital.features.reports.dto;

import co.istad.projectpracticum.phsardigital.features.reports.ContentReport;
import co.istad.projectpracticum.phsardigital.features.reports.ReportReason;
import co.istad.projectpracticum.phsardigital.features.reports.ReportStatus;
import co.istad.projectpracticum.phsardigital.features.reports.ReportTargetType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A report as both an admin and its own reporter see it.
 *
 * <p>One shape for both, because there is nothing here an admin may read that the
 * reporter may not: they wrote the accusation, and the decision is the answer they are
 * owed. What keeps the two apart is which rows each can reach, not which fields —
 * {@code ReportServiceImpl} answers 404 for somebody else's report.
 *
 * @param targetLabel what the target was called when the report was filed, which may no
 *                    longer be what it is called — or may name something since deleted
 */
public record ReportResponse(
        UUID uuid,
        String reporterId,
        ReportTargetType targetType,
        String targetId,
        String targetLabel,
        ReportReason reason,
        String note,
        ReportStatus status,
        LocalDateTime createdAt,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String decisionNote
) {

    public static ReportResponse of(ContentReport report) {
        return new ReportResponse(
                report.getUuid(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetLabel(),
                report.getReason(),
                report.getNote(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getReviewedBy(),
                report.getReviewedAt(),
                report.getDecisionNote());
    }
}
