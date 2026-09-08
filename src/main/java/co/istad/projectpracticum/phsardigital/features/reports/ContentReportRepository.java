package co.istad.projectpracticum.phsardigital.features.reports;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
