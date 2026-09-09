package co.istad.projectpracticum.phsardigital.features.reports.dto;

import co.istad.projectpracticum.phsardigital.features.reports.ReportReason;
import co.istad.projectpracticum.phsardigital.features.reports.ReportStatus;
import co.istad.projectpracticum.phsardigital.features.reports.ReportTargetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the moderation queue: the original report, plus just enough about what it
 * names to triage without opening it.
 *
 * <p>Every field {@link ReportResponse} carried is still here under the same name.
 *
 * <p>Deliberately lighter than the detail view. {@code ContentReport} snapshots
 * {@code targetLabel} precisely so the queue need not join anything to render, and that
 * reasoning still holds — what is added here is batched across the whole page rather than
 * fetched per row, and nothing that costs an aggregate per row is included.
 *
 * @param evidenceCount how many photographs are attached. A count rather than the URLs:
 *                      signing a link per attachment per row would hand out expiring URLs
 *                      to evidence nobody has opened.
 * @param targetImageUrl null when the target has been deleted, has no picture, or is a
 *                      kind of thing that has none
 * @param isVerifiedBuyer null on a reported review, where the question does not apply
 */
public record AdminReportRowResponse(
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
        String decisionNote,

        String targetImageUrl,
        BigDecimal targetPrice,
        String sellerId,
        String sellerName,
        int evidenceCount,
        String reporterName,
        Boolean isVerifiedBuyer,
        long targetPriorReportsCount
) {
}
