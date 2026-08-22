package co.istad.projectpracticum.phsardigital.features.purchases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    Page<Purchase> findByBuyerId(String buyerId, Pageable pageable);

    Optional<Purchase> findByUuidAndBuyerId(UUID uuid, String buyerId);

    Page<Purchase> findBySellerProfile_SellerId(String sellerId, Pageable pageable);

    Page<Purchase> findBySellerProfile_SellerIdAndStatus(String sellerId, PurchaseStatus status, Pageable pageable);

    long countByStatus(PurchaseStatus status);

    Page<Purchase> findByBuyerIdAndStatus(String buyerId, PurchaseStatus status, Pageable pageable);

    /**
     * Whether this buyer has ever completed an order with this shop — the test for
     * whether they may review it.
     */
    boolean existsByBuyerIdAndSellerProfile_SellerIdAndStatus(String buyerId,
                                                             String sellerId,
                                                             PurchaseStatus status);

    /**
     * Merchandise value over orders in one status — what buyers paid sellers, not
     * platform earnings.
     *
     * <p>The legacy column is floating point. Casting each order to cents before the
     * sum prevents binary aggregation drift in this report. This is a compatibility
     * bridge until every money column and calculation is migrated to {@code NUMERIC}
     * and {@link BigDecimal} with a versioned database migration.
     *
     * @return the cent-rounded sum, or zero when no order qualifies
     */
    @Query(value = """
            SELECT COALESCE(
                SUM(CAST(total_price AS numeric(19, 2))),
                CAST(0 AS numeric(19, 2))
            )
            FROM purchases
            WHERE status = :status
            """, nativeQuery = true)
    BigDecimal sumRoundedTotalPriceByStatus(@Param("status") String status);

    /**
     * Shops ranked by what their completed orders were worth.
     *
     * <p>Suspended shops are excluded in the query rather than filtered afterwards, so
     * a limit of ten returns ten shops that can actually be visited.
     *
     * @return {@code [sellerId, revenue, orderCount]} per shop, best first
     */
    @Query("SELECT p.sellerProfile.sellerId, SUM(p.totalPrice), COUNT(p) FROM Purchase p "
            + "WHERE p.status = :status AND p.createdAt >= :since AND p.sellerProfile.isActive = true "
            + "GROUP BY p.sellerProfile.sellerId "
            + "ORDER BY SUM(p.totalPrice) DESC")
    List<Object[]> rankSellersByRevenue(@Param("status") PurchaseStatus status,
                                        @Param("since") LocalDateTime since,
                                        Pageable pageable);

    /** As {@link #rankSellersByRevenue}, ordered by how many orders rather than their value. */
    @Query("SELECT p.sellerProfile.sellerId, SUM(p.totalPrice), COUNT(p) FROM Purchase p "
            + "WHERE p.status = :status AND p.createdAt >= :since AND p.sellerProfile.isActive = true "
            + "GROUP BY p.sellerProfile.sellerId "
            + "ORDER BY COUNT(p) DESC")
    List<Object[]> rankSellersByOrders(@Param("status") PurchaseStatus status,
                                       @Param("since") LocalDateTime since,
                                       Pageable pageable);

    /**
     * The same totals for a known set of shops, to fill in the board when it was
     * ranked on something else. One query for the whole page rather than one per row.
     *
     * @return {@code [sellerId, revenue, orderCount]}; shops with no orders are absent
     */
    @Query("SELECT p.sellerProfile.sellerId, SUM(p.totalPrice), COUNT(p) FROM Purchase p "
            + "WHERE p.status = :status AND p.createdAt >= :since AND p.sellerProfile.sellerId IN :sellerIds "
            + "GROUP BY p.sellerProfile.sellerId")
    List<Object[]> salesForSellers(@Param("status") PurchaseStatus status,
                                   @Param("since") LocalDateTime since,
                                   @Param("sellerIds") Collection<String> sellerIds);

    /**
     * What else went into the basket alongside a listing, most frequent first. Counts
     * orders, not items, so one buyer ordering ten of something does not outweigh ten
     * buyers ordering one each.
     *
     * <p>An order is always for a single shop, so everything this returns comes from
     * the same shop as {@code listingUuid} — a property of the checkout, not a
     * restriction imposed here. Marketplace-wide suggestions come from the category
     * tier instead.
     *
     * @param statuses which orders count as real; a PENDING order is a request the
     *                 seller has not accepted and a CANCELLED one came to nothing, so
     *                 counting either would let anyone shape a rival's suggestions by
     *                 placing orders they never intend to pay for
     * @return {@code [listingUuid, orderCount]}, strongest first
     */
    @Query("SELECT peer.listing.uuid, COUNT(DISTINCT peer.purchase.uuid) "
            + "FROM PurchaseItem source JOIN PurchaseItem peer ON peer.purchase = source.purchase "
            + "WHERE source.listing.uuid = :listingUuid "
            + "AND peer.listing.uuid <> :listingUuid "
            + "AND source.purchase.status IN :statuses "
            + "GROUP BY peer.listing.uuid "
            + "ORDER BY COUNT(DISTINCT peer.purchase.uuid) DESC")
    List<Object[]> rankBoughtTogetherWith(@Param("listingUuid") UUID listingUuid,
                                          @Param("statuses") Collection<PurchaseStatus> statuses,
                                          Pageable pageable);
}
