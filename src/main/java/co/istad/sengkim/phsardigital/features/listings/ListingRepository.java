package co.istad.sengkim.phsardigital.features.listings;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    @Query("SELECT l FROM Listing l " +
            "LEFT JOIN FETCH l.category " +
            "LEFT JOIN FETCH l.imagesList " +
            "WHERE l.uuid = :uuid")
    Optional<Listing> findByUuidWithDetails(@Param("uuid") UUID uuid);


    Page<Listing> findByStatus(ListingStatus status,Pageable pageable);
}