package co.istad.projectpracticum.phsardigital.features.seller.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SellerApplicationDocumentRepository extends JpaRepository<SellerApplicationDocument, UUID> {

    List<SellerApplicationDocument> findByApplication_UuidOrderByUploadedAtAsc(UUID applicationUuid);

    /**
     * Batch lookup so listing a page of applications stays at one document query
     * instead of one per row.
     */
    List<SellerApplicationDocument> findByApplication_UuidIn(Collection<UUID> applicationUuids);

    /**
     * The admin's review backlog measured in files rather than applications: every
     * document uploaded against an application still sitting in {@code status}.
     *
     * <p>A document carries no verification state of its own, so "pending" is a
     * property of the application it belongs to.
     */
    @Query("SELECT COUNT(d) FROM SellerApplicationDocument d WHERE d.application.status = :status")
    long countByApplicationStatus(@Param("status") ApplicationStatus status);
}
