package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * The public browse query. Filters on the shop as well as the listing, because
     * suspending a shop only flips {@code SellerProfile.isActive} — its listings stay
     * {@code ACTIVE}, and without this a suspended shop's products keep appearing in
     * search and stay purchasable.
     */
    Page<Listing> findByStatusAndSellerProfile_IsActiveTrue(ListingStatus status, Pageable pageable);

    boolean existsBySlug(String slug);


    Page<Listing> findBySellerProfileAndStatus(SellerProfile seller, ListingStatus status, Pageable pageable);

    /**
     * How many listings count against a seller's subscription plan. Excludes one
     * status rather than listing the counted ones, so a new {@code ListingStatus}
     * counts by default instead of silently slipping past the limit.
     */
    long countBySellerProfile_SellerIdAndStatusNot(String sellerId, ListingStatus status);

    long countByStatus(ListingStatus status);

    Page<Listing> findBySellerProfile_SellerId(String sellerId, Pageable pageable);

    Page<Listing> findBySellerProfile_SellerIdAndStatus(String sellerId, ListingStatus status, Pageable pageable);

    /**
     * Takes a row lock on the listing, for the read-check-write around stock.
     *
     * <p>Confirming an order read {@code stockQty}, compared it, then wrote the
     * decrement — with nothing between the read and the write, two confirms landing
     * together both saw enough stock and both subtracted, driving it negative. Once it
     * goes negative the {@code == 0} test never fires either, so the listing never
     * flips to {@code SOLD_OUT} and keeps selling.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Listing l WHERE l.uuid = :uuid")
    Optional<Listing> findByUuidForUpdate(@Param("uuid") UUID uuid);
}