package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A photograph or screenshot a reporter attached to their complaint.
 *
 * <p>A row rather than an array column, matching {@code PurchaseDeliveryPhoto}: the file
 * is a real reference, so deleting the underlying object cannot leave a report pointing
 * at nothing, and the order the reporter arranged them in survives.
 *
 * <p>Evidence lives in the <em>private</em> bucket and is answered as an expiring
 * presigned URL. A scam complaint routinely carries a photograph of somebody's doorstep,
 * a delivery slip with an address on it, or a chat screenshot — none of which may be
 * anonymously readable to anyone who guesses the object name.
 */
@Entity
@Table(name = "report_evidence")
@Getter
@Setter
@NoArgsConstructor
public class ReportEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "report_uuid", nullable = false)
    private ContentReport report;

    /**
     * Eager because evidence is only ever loaded when a report is being read, and that is
     * exactly when every attachment on it needs a URL signed.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "file_id", nullable = false)
    private FileUpload file;

    /** The order the reporter attached them in, which is often the order of events. */
    @Column(name = "sort_order")
    private Integer sortOrder;
}
