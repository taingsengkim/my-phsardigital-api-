package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.reports.dto.AdminReportDetailResponse;
import co.istad.projectpracticum.phsardigital.features.reports.dto.AdminReportRowResponse;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportDecisionRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** The moderation queue. */
public interface AdminReportService {

    Page<AdminReportRowResponse> list(ReportStatus status, ReportTargetType targetType,
                                      int pageNumber, int pageSize);

    AdminReportDetailResponse findOne(UUID uuid);

    AdminReportDetailResponse resolve(UUID uuid, ReportDecisionRequest request);

    AdminReportDetailResponse dismiss(UUID uuid, ReportDecisionRequest request);
}
