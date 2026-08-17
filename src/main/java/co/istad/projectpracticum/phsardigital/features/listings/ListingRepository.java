package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    @Query("SELECT l FROM Listing l " +
            "LEFT JOIN FETCH l.category " +
            "LEFT JOIN FETCH l.images " +
            "WHERE l.uuid = :uuid")
    Optional<Listing> findByUuidWithDetails(@Param("uuid") UUID uuid);


    Page<Listing> findByStatus(ListingStatus status,Pageable pageable);

    boolean existsBySlug(String slug);


    Page<Listing> findBySellerProfileAndStatus(SellerProfile seller, ListingStatus status, Pageable pageable);

    /**
     * How many listings count against a seller's subscription plan. Excludes one
     * status rather than listing the counted ones, so a new {@code ListingStatus}
     * counts by default instead of silently slipping past the limit.
     */
    long countBySellerProfile_SellerIdAndStatusNot(String sellerId, ListingStatus status);
}