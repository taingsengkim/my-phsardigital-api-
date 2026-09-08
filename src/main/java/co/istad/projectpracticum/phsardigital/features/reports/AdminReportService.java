package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportDecisionRequest;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** The moderation queue. */
public interface AdminReportService {

    Page<ReportResponse> list(ReportStatus status, ReportTargetType targetType,
                              int pageNumber, int pageSize);

    ReportResponse findOne(UUID uuid);

    ReportResponse resolve(UUID uuid, ReportDecisionRequest request);

    ReportResponse dismiss(UUID uuid, ReportDecisionRequest request);
}
