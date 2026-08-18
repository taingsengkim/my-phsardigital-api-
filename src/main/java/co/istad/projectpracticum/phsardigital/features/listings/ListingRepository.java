package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    // Related products. All three fetch the category, shop and thumbnail the card is
    // drawn from — every one of those is a ManyToOne, so join-fetching them is safe to
    // paginate, and without it a strip of eight cards is a strip of eight extra
    // queries. The gallery is deliberately not fetched: it is a collection, so fetching
    // it would force Hibernate to paginate in memory over the whole result.

    /**
     * Category peers a visitor could actually buy, nearest in price first.
     *
     * <p>Price proximity rather than recency: a phone case sitting beside a phone is a
     * worse suggestion than another case, however new the phone is. Sales break the tie,
     * so among equally-priced peers the shelf leans towards what moves.
     *
     * <p>{@code isActive} is checked for the same reason the browse query checks it —
     * suspending a shop leaves its listings {@code ACTIVE}, and a related strip would
     * quietly put them back in front of buyers.
     */
    @Query("SELECT l FROM Listing l "
            + "JOIN FETCH l.category "
            + "JOIN FETCH l.sellerProfile s "
            + "LEFT JOIN FETCH l.thumbnailFile "
            + "WHERE l.category = :category "
            + "AND l.uuid <> :excludeUuid "
            + "AND l.status = :status "
            + "AND s.isActive = true "
            + "ORDER BY ABS(l.price - :price), l.sold DESC")
    List<Listing> findCategoryPeers(@Param("category") Category category,
                                    @Param("excludeUuid") UUID excludeUuid,
                                    @Param("price") Double price,
                                    @Param("status") ListingStatus status,
                                    Pageable pageable);

    /** The rest of one shop's window, best-selling first. */
    @Query("SELECT l FROM Listing l "
            + "JOIN FETCH l.category "
            + "JOIN FETCH l.sellerProfile s "
            + "LEFT JOIN FETCH l.thumbnailFile "
            + "WHERE s = :seller "
            + "AND l.uuid <> :excludeUuid "
            + "AND l.status = :status "
            + "AND s.isActive = true "
            + "ORDER BY l.sold DESC, l.createdAt DESC")
    List<Listing> findShopPeers(@Param("seller") SellerProfile seller,
                                @Param("excludeUuid") UUID excludeUuid,
                                @Param("status") ListingStatus status,
                                Pageable pageable);

    /**
     * Reloads listings named by an aggregate, keeping only the ones still on sale.
     *
     * <p>Order history remembers listings that have since been archived, sold out or
     * taken down, so what comes back from a co-purchase ranking cannot be trusted to be
     * buyable. The caller restores the ranking's order afterwards — this is a filter,
     * not a sort.
     */
    @Query("SELECT l FROM Listing l "
            + "JOIN FETCH l.category "
            + "JOIN FETCH l.sellerProfile s "
            + "LEFT JOIN FETCH l.thumbnailFile "
            + "WHERE l.uuid IN :uuids "
            + "AND l.status = :status "
            + "AND s.isActive = true")
    List<Listing> findBuyableByUuidIn(@Param("uuids") Collection<UUID> uuids,
                                      @Param("status") ListingStatus status);
}