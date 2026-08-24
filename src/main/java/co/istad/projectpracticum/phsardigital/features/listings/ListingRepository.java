package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {

    /** Both back {@code ListingFileReferences}. */
    boolean existsByThumbnailFile_ObjectName(String objectName);

    boolean existsByImages_File_ObjectName(String objectName);
    @Query("SELECT l FROM Listing l " +
            "LEFT JOIN FETCH l.category " +
            "LEFT JOIN FETCH l.images " +
            "WHERE l.uuid = :uuid")
    Optional<Listing> findByUuidWithDetails(@Param("uuid") UUID uuid);

    /**
     * The same listing by the slug its public URL is built from. Slugs are unique across
     * the table, so this is a lookup rather than a search.
     */
    @Query("SELECT l FROM Listing l " +
            "LEFT JOIN FETCH l.category " +
            "LEFT JOIN FETCH l.images " +
            "WHERE l.slug = :slug")
    Optional<Listing> findBySlugWithDetails(@Param("slug") String slug);

    /**
     * The filtered browse query. Overridden only to hang an {@code @EntityGraph} on it:
     * a specification produces no fetch joins, so every card would otherwise load its
     * category, shop and thumbnail one query at a time.
     */
    @Override
    @EntityGraph(attributePaths = {"category", "sellerProfile", "thumbnailFile"})
    Page<Listing> findAll(Specification<Listing> spec, Pageable pageable);


    Page<Listing> findByStatus(ListingStatus status,Pageable pageable);

    /**
     * The public browse query. Filters on the shop as well as the listing, because
     * suspending a shop only flips {@code SellerProfile.isActive} — its listings stay
     * {@code ACTIVE}, and without this a suspended shop's products keep appearing in
     * search and stay purchasable.
     */
    Page<Listing> findByStatusAndSellerProfile_IsActiveTrue(ListingStatus status, Pageable pageable);

    Page<Listing> findByStatusAndSellerProfile_IsActiveTrueAndCategory_IsActiveTrueAndCategory_IsDeletedFalse(
            ListingStatus status, Pageable pageable);

    boolean existsBySlug(String slug);


    Page<Listing> findBySellerProfileAndStatus(SellerProfile seller, ListingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "sellerProfile", "thumbnailFile"})
    @Query("SELECT l FROM Listing l "
            + "WHERE l.sellerProfile = :seller "
            + "AND l.status = :status "
            + "AND l.stockQty > :minimumStock "
            + "AND l.category.uuid IN :categoryUuids")
    Page<Listing> findPublicBySeller(
            @Param("seller") SellerProfile seller,
            @Param("status") ListingStatus status,
            @Param("minimumStock") Integer minimumStock,
            @Param("categoryUuids") Collection<UUID> categoryUuids,
            Pageable pageable);

    /**
     * How many listings count against a seller's subscription plan. Excludes one
     * status rather than listing the counted ones, so a new {@code ListingStatus}
     * counts by default instead of silently slipping past the limit.
     */
    long countBySellerProfile_SellerIdAndStatusNot(String sellerId, ListingStatus status);

    long countByStatus(ListingStatus status);

    /**
     * Status alone is not sellability: stock and shop must permit sale, while the
     * caller supplies categories whose complete ancestry is publicly available.
     */
    @Query("SELECT COUNT(l) FROM Listing l "
            + "WHERE l.status = :status "
            + "AND l.stockQty > 0 "
            + "AND l.sellerProfile.isActive = true "
            + "AND l.category.uuid IN :categoryUuids")
    long countBuyableByStatusInCategories(
            @Param("status") ListingStatus status,
            @Param("categoryUuids") Collection<UUID> categoryUuids);

    boolean existsByCategory_Uuid(UUID categoryUuid);

    boolean existsByCategory_UuidIn(Collection<UUID> categoryUuids);

    /** Locks affected listings before a category-schema rewrite; attributes load in-transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Listing> findAllByCategory_UuidIn(Collection<UUID> categoryUuids);

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

    /** Serializes complete-set listing and attribute edits to prevent lost updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Listing l WHERE l.uuid = :uuid")
    Optional<Listing> findByUuidForEdit(@Param("uuid") UUID uuid);

    // Related products. All three join-fetch the category, shop and thumbnail the card
    // is drawn from — all ManyToOne, so safe to paginate. The gallery is deliberately
    // left out: it is a collection, and fetching it would force Hibernate to paginate
    // in memory over the whole result.

    /**
     * Category peers a visitor could actually buy, nearest in price first — a phone
     * case beside a phone is a worse suggestion than another case, however new the
     * phone is. Sales break the tie.
     *
     * <p>{@code isActive} is checked for the same reason the browse query checks it:
     * suspending a shop leaves its listings {@code ACTIVE}, and a related strip would
     * quietly put them back in front of buyers.
     *
     * @param price the source listing's effective price, which is what nearness is
     *              measured against
     */
    @Query("SELECT l FROM Listing l "
            + "JOIN FETCH l.category "
            + "JOIN FETCH l.sellerProfile s "
            + "LEFT JOIN FETCH l.thumbnailFile "
            + "WHERE l.category = :category "
            + "AND l.uuid <> :excludeUuid "
            + "AND l.status = :status "
            + "AND s.isActive = true "
            + "AND l.category.isActive = true "
            + "AND l.category.isDeleted = false "
            + "ORDER BY ABS(COALESCE(l.discountPrice, l.fullPrice) - :price), l.sold DESC")
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
            + "AND l.category.isActive = true "
            + "AND l.category.isDeleted = false "
            + "ORDER BY l.sold DESC, l.createdAt DESC")
    List<Listing> findShopPeers(@Param("seller") SellerProfile seller,
                                @Param("excludeUuid") UUID excludeUuid,
                                @Param("status") ListingStatus status,
                                Pageable pageable);

    /**
     * Reloads listings named by an aggregate, keeping only the ones still on sale — a
     * co-purchase ranking is drawn from order history and cannot be trusted to name
     * buyable products. A filter, not a sort: the caller restores the ranking's order.
     */
    @Query("SELECT l FROM Listing l "
            + "JOIN FETCH l.category "
            + "JOIN FETCH l.sellerProfile s "
            + "LEFT JOIN FETCH l.thumbnailFile "
            + "WHERE l.uuid IN :uuids "
            + "AND l.status = :status "
            + "AND s.isActive = true "
            + "AND l.category.isActive = true "
            + "AND l.category.isDeleted = false")
    List<Listing> findBuyableByUuidIn(@Param("uuids") Collection<UUID> uuids,
                                      @Param("status") ListingStatus status);
}
