package co.istad.projectpracticum.phsardigital.features.reports.dto;

import co.istad.projectpracticum.phsardigital.features.reports.ReportReason;
import co.istad.projectpracticum.phsardigital.features.reports.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param targetId the reported thing's id — a UUID for a listing or a review, the
 *                 seller id for a shop. Text rather than a UUID because one field has
 *                 to carry both shapes; {@code ReportTargetResolver} checks it against
 *                 the type
 * @param note     the reporter's own account of the problem. Optional, except with
 *                 {@link ReportReason#OTHER}, which says nothing on its own
 * @param evidenceObjectNames photographs or screenshots backing the complaint, already
 *                 uploaded through {@code POST /api/v1/files/report-evidence}. Optional.
 *                 Every one must be a file this reporter uploaded — naming somebody
 *                 else's is refused, since the alternative is a way to have the server
 *                 sign URLs for arbitrary private objects.
 */
public record ReportRequest(
        @NotNull(message = "Say what kind of thing you are reporting")
        ReportTargetType targetType,

        @NotBlank(message = "Say what you are reporting")
        @Size(max = 100, message = "That is not a valid id")
        String targetId,

        @NotNull(message = "A reason is required")
        ReportReason reason,

        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note,

        @Size(max = 5, message = "Attach at most 5 pieces of evidence")
        List<String> evidenceObjectNames
) {
}
