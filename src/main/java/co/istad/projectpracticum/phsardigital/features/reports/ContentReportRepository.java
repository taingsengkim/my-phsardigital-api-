package co.istad.projectpracticum.phsardigital.features.reports;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link JpaSpecificationExecutor} backs the admin queue, whose two filters are both
 * optional — four query methods for the combinations, or one specification.
 */
public interface ContentReportRepository extends JpaRepository<ContentReport, UUID>,
        JpaSpecificationExecutor<ContentReport> {

    /**
     * Exclusive, for an admin's decision. Two admins working the queue can otherwise
     * both read an open report and both close it, and the second write silently
     * replaces the first one's reasoning.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ContentReport r WHERE r.uuid = :uuid")
    Optional<ContentReport> findByUuidForUpdate(@Param("uuid") UUID uuid);

    /**
     * Whether this person already has a complaint outstanding about this exact thing.
     * Scoped to {@code OPEN} on purpose: somebody whose first report was dismissed may
     * report the same listing again when it changes, they simply may not file the same
     * one twice while it is still waiting.
     */
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            String reporterId, ReportTargetType targetType, String targetId, ReportStatus status);

    Page<ContentReport> findByReporterIdOrderByCreatedAtDesc(String reporterId, Pageable pageable);

    /** Backs {@code ReportFileReferences}. */
    boolean existsByEvidence_File_ObjectName(String objectName);

    /**
     * How many complaints have been filed against one thing, and how many of those an
     * admin agreed with — the two numbers that turn a single report into a pattern.
     *
     * <p>Counted per target rather than per report, and grouped by status in one query so
     * a detail page costs one round trip instead of two counts. {@code RESOLVED} is the
     * closest thing this system has to an upheld violation: it means an admin looked and
     * did something, while {@code DISMISSED} means they looked and there was nothing to
     * answer.
     *
     * @return {@code [status, count]} for each status this target has reports in; a
     *         status with none is absent rather than a zero row
     */
    @Query("SELECT r.status, COUNT(r) FROM ContentReport r "
            + "WHERE r.targetType = :targetType AND r.targetId = :targetId "
            + "AND r.uuid <> :excluding "
            + "GROUP BY r.status")
    List<Object[]> countByStatusForTarget(@Param("targetType") ReportTargetType targetType,
                                          @Param("targetId") String targetId,
                                          @Param("excluding") UUID excluding);

    /**
     * The same counts for a page of targets at once, so the queue does not cost two
     * aggregates per row.
     *
     * @return {@code [targetType, targetId, status, count]}
     */
    @Query("SELECT r.targetType, r.targetId, r.status, COUNT(r) FROM ContentReport r "
            + "WHERE r.targetId IN :targetIds "
            + "GROUP BY r.targetType, r.targetId, r.status")
    List<Object[]> countByStatusForTargets(@Param("targetIds") Collection<String> targetIds);

    /** How many complaints one person has ever filed — reporter credibility, on the detail page. */
    long countByReporterId(String reporterId);
}
