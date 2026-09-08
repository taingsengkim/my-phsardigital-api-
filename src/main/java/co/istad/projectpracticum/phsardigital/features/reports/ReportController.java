package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportRequest;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Reporting a listing, a shop or a review.
 *
 * <p>Open to any signed-in caller rather than to buyers alone: a seller who finds their
 * own photographs on somebody else's listing is exactly the person best placed to say
 * so.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse file(@Valid @RequestBody ReportRequest request) {
        return reportService.file(request);
    }

    /** The caller's own reports, newest first, so they can see what came of them. */
    @GetMapping("/me")
    public Page<ReportResponse> myReports(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize) {
        return reportService.findMine(pageNumber, pageSize);
    }

    /**
     * Declared after {@code /me}, though the ordering is cosmetic: a literal segment
     * outranks a variable one, and {@code me} is not a UUID in any case.
     */
    @GetMapping("/{uuid}")
    public ReportResponse findOne(@PathVariable UUID uuid) {
        return reportService.findMine(uuid);
    }
}
