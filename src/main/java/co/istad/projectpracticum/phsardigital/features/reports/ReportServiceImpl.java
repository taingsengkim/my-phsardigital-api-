package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportRequest;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ContentReportRepository reportRepository;
    private final ReportTargetResolver targetResolver;

    /**
     * Files a complaint. Grants nothing and hides nothing on its own — a report is a
     * request for a person to look, not a takedown, so a listing stays up until an
     * admin decides otherwise. Anything else would hand every competitor a delete
     * button.
     */
    @Override
    @Transactional
    public ReportResponse file(ReportRequest request) {
        String reporterId = AuthUtils.extractUserId();
        String targetId = request.targetId().trim();
        String note = trimToNull(request.note());

        if (request.reason() == ReportReason.OTHER && note == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tell us what the problem is when the reason is OTHER.");
        }

        // Throws 404 if there is nothing there, so the queue only ever holds reports
        // about things that existed when they were filed.
        ReportTargetResolver.ResolvedTarget target =
                targetResolver.resolve(request.targetType(), targetId);

        if (reporterId.equals(target.ownerId())) {
            // Almost always a mistake — a seller tapping report on their own listing.
            // Somebody who genuinely wants their own content taken down has the edit
            // and delete endpoints for it.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot report your own " + request.targetType().name().toLowerCase() + ".");
        }

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporterId, request.targetType(), targetId, ReportStatus.OPEN)) {
            // Filing again changes nothing and buries the queue under one person's
            // repeats. Their first report is still open and still being read.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have already reported this, and we are still looking at it.");
        }

        ContentReport report = new ContentReport();
        report.setReporterId(reporterId);
        report.setTargetType(request.targetType());
        report.setTargetId(targetId);
        report.setTargetLabel(target.label());
        report.setReason(request.reason());
        report.setNote(note);
        report.setStatus(ReportStatus.OPEN);

        ContentReport saved = reportRepository.save(report);
        log.info("Report {} filed by {} against {} {} for {}", saved.getUuid(), reporterId,
                saved.getTargetType(), saved.getTargetId(), saved.getReason());
        return ReportResponse.of(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> findMine(int pageNumber, int pageSize) {
        return reportRepository
                .findByReporterIdOrderByCreatedAtDesc(
                        AuthUtils.extractUserId(), PageRequest.of(pageNumber, pageSize))
                .map(ReportResponse::of);
    }

    /**
     * Somebody else's report answers 404 rather than 403. Who has reported what is
     * exactly the thing a reported seller would like to find out, and a 403 would
     * confirm the report exists.
     */
    @Override
    @Transactional(readOnly = true)
    public ReportResponse findMine(UUID uuid) {
        String reporterId = AuthUtils.extractUserId();
        ContentReport report = reportRepository.findById(uuid)
                .filter(found -> found.getReporterId().equals(reporterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found."));
        return ReportResponse.of(report);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
