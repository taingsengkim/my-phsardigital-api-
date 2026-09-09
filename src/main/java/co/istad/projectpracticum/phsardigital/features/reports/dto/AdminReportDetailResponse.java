package co.istad.projectpracticum.phsardigital.features.reports.dto;

import co.istad.projectpracticum.phsardigital.features.reports.ReportReason;
import co.istad.projectpracticum.phsardigital.features.reports.ReportStatus;
import co.istad.projectpracticum.phsardigital.features.reports.ReportTargetType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Everything an admin needs to decide one report without opening four other screens.
 *
 * <p>Every field {@link ReportResponse} carried is still here under the same name, so a
 * client reading the old shape keeps working; the rest is added alongside.
 *
 * @param evidenceUrls  presigned and <em>expiring</em>, because evidence lives in the
 *                      private bucket. Load them from the response in hand rather than
 *                      caching them — a drawer left open will outlive its links.
 * @param targetDetails the reported thing as it stands now, or <b>null when it has been
 *                      deleted</b>. Reports outlive their targets on purpose, so this
 *                      being absent is ordinary; fall back to {@code targetLabel}.
 * @param targetPriorReportsCount    other complaints against this same target, this one
 *                                   excluded
 * @param targetPriorViolationsCount how many of those an admin resolved rather than
 *                                   dismissed — the closest thing to an upheld violation,
 *                                   since resolving means somebody looked and acted
 */
public record AdminReportDetailResponse(
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

        List<String> evidenceUrls,
        ReportReporterResponse reporter,
        ReportTargetDetailsResponse targetDetails,
        long targetPriorReportsCount,
        long targetPriorViolationsCount
) {
}
