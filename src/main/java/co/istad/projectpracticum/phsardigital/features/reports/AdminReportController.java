package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.reports.dto.AdminReportDetailResponse;
import co.istad.projectpracticum.phsardigital.features.reports.dto.AdminReportRowResponse;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportDecisionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The moderation queue.
 *
 * <p>Closing a report records a decision; it does not act on what was reported. The
 * acting is done with the endpoints that already exist — {@code
 * /api/v1/admin/listings/{uuid}/suspend}, {@code /api/v1/admin/sellers/{sellerId}/suspend},
 * {@code DELETE /api/v1/admin/reviews/{reviewUuid}} — so that one suspension can answer
 * the twelve reports that prompted it.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Validated
public class AdminReportController {

    private final AdminReportService adminReportService;

    /** Both filters are optional; the useful one is {@code status=OPEN}. */
    @GetMapping
    public Page<AdminReportRowResponse> list(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize) {
        return adminReportService.list(status, targetType, pageNumber, pageSize);
    }

    @GetMapping("/{uuid}")
    public AdminReportDetailResponse findOne(@PathVariable UUID uuid) {
        return adminReportService.findOne(uuid);
    }

    /** Something was done about it. */
    @PatchMapping("/{uuid}/resolve")
    public AdminReportDetailResponse resolve(@PathVariable UUID uuid,
                                  @Valid @RequestBody(required = false) ReportDecisionRequest request) {
        return adminReportService.resolve(uuid, request);
    }

    /** Looked at, and there was nothing to answer. */
    @PatchMapping("/{uuid}/dismiss")
    public AdminReportDetailResponse dismiss(@PathVariable UUID uuid,
                                  @Valid @RequestBody(required = false) ReportDecisionRequest request) {
        return adminReportService.dismiss(uuid, request);
    }
}
