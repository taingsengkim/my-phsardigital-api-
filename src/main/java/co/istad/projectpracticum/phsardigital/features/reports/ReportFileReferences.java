package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A photograph somebody attached to a moderation report. */
@Component
@RequiredArgsConstructor
public class ReportFileReferences implements FileReferenceCheck {

    private final ContentReportRepository reportRepository;

    @Override
    public String referenceName() {
        return "evidence on a report";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return reportRepository.existsByEvidence_File_ObjectName(objectName);
    }

    /**
     * Refused rather than detached.
     *
     * <p>The photograph is the case. A reporter who could delete it after filing could
     * withdraw the evidence while leaving the accusation standing, and an admin closing
     * the report months later would be deciding on a complaint whose proof had quietly
     * gone. Reports are kept after they close for exactly this reason — see
     * {@link ContentReport} — and evidence is kept on the same terms.
     */
    @Override
    public boolean isMandatory() {
        return true;
    }
}
