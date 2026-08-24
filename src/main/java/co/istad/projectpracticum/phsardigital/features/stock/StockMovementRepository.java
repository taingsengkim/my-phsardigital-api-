package co.istad.projectpracticum.phsardigital.features.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByListing_Uuid(UUID listingUuid, Pageable pageable);

    /**
     * Whether this reference already moved stock. Confirming an order twice, or a
     * counter sale arriving twice from a till that lost its connection, must not take
     * the goods off the shelf twice.
     */
    boolean existsByRefUuidAndReason(UUID refUuid, StockMovementReason reason);

    /** The sum of every movement for a listing — what {@code stock_qty} should equal. */
    @Query("SELECT COALESCE(SUM(m.delta), 0) FROM StockMovement m WHERE m.listing.uuid = :listingUuid")
    int ledgerBalance(@Param("listingUuid") UUID listingUuid);
}
