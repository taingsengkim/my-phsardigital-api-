package co.istad.projectpracticum.phsardigital.features.seller.application;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
