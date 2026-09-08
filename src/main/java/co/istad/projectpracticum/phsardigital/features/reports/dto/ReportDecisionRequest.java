package co.istad.projectpracticum.phsardigital.features.reports.dto;

import jakarta.validation.constraints.Size;

/**
 * An admin's answer to a report.
 *
 * <p>The note is optional but worth writing: it is what the next admin reads when the
 * same shop is reported again, and the only place the reasoning survives once the queue
 * has moved on.
 */
public record ReportDecisionRequest(
        @Size(max = 1000, message = "Note must not exceed 1000 characters")
        String note
) {
}
