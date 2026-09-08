package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportRequest;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** Filing a report, and following what came of it. */
public interface ReportService {

    ReportResponse file(ReportRequest request);

    Page<ReportResponse> findMine(int pageNumber, int pageSize);

    ReportResponse findMine(UUID uuid);
}
