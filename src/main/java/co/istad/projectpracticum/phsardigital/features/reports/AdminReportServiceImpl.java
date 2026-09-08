package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportDecisionRequest;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What an admin does with the queue.
 *
 * <p>Closing a report does not touch what it complains about. Suspending the listing,
 * suspending the shop and deleting the review are separate endpoints that already
 * exist, and keeping them separate is what lets one report be resolved by an action
 * taken against a different target — five reports about one shop are usually answered
 * by one suspension, not five.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReportServiceImpl implements AdminReportService {

    private final ContentReportRepository reportRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> list(ReportStatus status, ReportTargetType targetType,
                                     int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(
                pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reportRepository.findAll(matching(status, targetType), pageable)
                .map(ReportResponse::of);
    }

    /**
     * Both filters are optional, so an empty conjunction — every report — is the
     * correct result of asking for neither.
     */
    private static Specification<ContentReport> matching(ReportStatus status,
                                                         ReportTargetType targetType) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (targetType != null) {
                predicates.add(builder.equal(root.get("targetType"), targetType));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse findOne(UUID uuid) {
        return ReportResponse.of(reportRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found.")));
    }

    @Override
    @Transactional
    public ReportResponse resolve(UUID uuid, ReportDecisionRequest request) {
        return close(uuid, ReportStatus.RESOLVED, request);
    }

    @Override
    @Transactional
    public ReportResponse dismiss(UUID uuid, ReportDecisionRequest request) {
        return close(uuid, ReportStatus.DISMISSED, request);
    }

    /**
     * Closing is refused on a report that is already closed rather than allowed to
     * overwrite it. Two admins working the same queue would otherwise silently replace
     * each other's reasoning, and the second one would never know the first had looked.
     */
    private ReportResponse close(UUID uuid, ReportStatus outcome, ReportDecisionRequest request) {
        ContentReport report = reportRepository.findByUuidForUpdate(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found."));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This report was already " + report.getStatus().name().toLowerCase()
                            + " by " + report.getReviewedBy() + ".");
        }

        String adminId = AuthUtils.extractUserId();
        report.setStatus(outcome);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        report.setDecisionNote(trimToNull(request == null ? null : request.note()));

        log.info("Report {} against {} {} {} by {}", report.getUuid(), report.getTargetType(),
                report.getTargetId(), outcome.name().toLowerCase(), adminId);
        return ReportResponse.of(reportRepository.save(report));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
