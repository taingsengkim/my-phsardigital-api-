package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One complaint about one listing, shop or review, and what an admin did about it.
 *
 * <p>Rows are kept after they are closed rather than deleted. A shop suspended for
 * counterfeits is a decision somebody may have to defend months later, and the reports
 * behind it are the only record of why it was looked at in the first place.
 */
@Entity
@Table(name = "content_reports", indexes = {
        // The admin queue: open reports, newest first.
        @Index(name = "idx_content_reports_status_created", columnList = "status, created_at"),
        // Everything filed against one listing or shop, which is what turns a single
        // complaint into a pattern worth acting on.
        @Index(name = "idx_content_reports_target", columnList = "target_type, target_id"),
        // Backs GET /api/v1/reports/me, and the duplicate check on filing.
        @Index(name = "idx_content_reports_reporter", columnList = "reporter_id, status")
})
@Getter
@Setter
@NoArgsConstructor
public class ContentReport extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    /** The Keycloak subject who filed it, and the only non-admin who may read it back. */
    @Column(name = "reporter_id", nullable = false)
    private String reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    /**
     * The reported thing's id, as text. A listing and a review are UUIDs and a shop is
     * a Keycloak subject, so this holds whichever the type calls for — see
     * {@link ReportTargetType} for why it is not a foreign key.
     */
    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    /**
     * What the target was called when the report was filed — a product title, a shop
     * name, the opening of a review.
     *
     * <p>Snapshotted rather than looked up on read, for the same reason
     * {@code Payment.reference} is: a report has to stay readable after the listing it
     * names is deleted, and an admin queue that costs a join per row to render is a
     * queue that gets rendered slowly.
     */
    @Column(name = "target_label", length = 200)
    private String targetLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    /** The reporter's own words. Optional, except on {@link ReportReason#OTHER}. */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * What the admin decided and why.
     *
     * <p>Kept apart from {@link #note}: that is the accusation, this is the answer to
     * it, and overwriting one with the other would lose what was actually alleged.
     */
    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    /**
     * What the reporter attached to back the complaint up.
     *
     * <p>Cascaded and orphan-removed, so evidence belongs to its report and goes with it.
     * The files themselves are not deleted here — {@code ReportFileReferences} refuses to
     * delete an object a report still cites, since the photograph is the case.
     */
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ReportEvidence> evidence = new ArrayList<>();
}
