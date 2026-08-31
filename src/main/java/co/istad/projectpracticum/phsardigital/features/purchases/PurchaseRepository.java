package co.istad.projectpracticum.phsardigital.features.purchases;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    /** Serializes every state transition for one order before inventory is touched. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.uuid = :uuid")
    Optional<Purchase> findByUuidForUpdate(@Param("uuid") UUID uuid);

    /** Backs the delivery-photo detach in {@code PurchaseFileListener}. */
    List<Purchase> findAllByDeliveryPhotos_File_ObjectName(String objectName);

    Page<Purchase> findByBuyerId(String buyerId, Pageable pageable);

    Optional<Purchase> findByUuidAndBuyerId(UUID uuid, String buyerId);

    Page<Purchase> findBySellerProfile_SellerId(String sellerId, Pageable pageable);

    Page<Purchase> findBySellerProfile_SellerIdAndStatus(String sellerId, PurchaseStatus status, Pageable pageable);

    /**
     * One shop's order counts and takings, broken down by state — the whole of a seller's
     * order dashboard in a single round trip rather than one query per tile.
     *
     * <p>Native and cent-cast for the same reason as {@link #sumRoundedTotalPriceByStatus}:
     * the money column is floating point, and a seller's revenue is not a number to
     * report with binary drift in it.
     *
     * @return {@code [status, orderCount, revenue]} per state the shop has orders in;
     *         a state with no orders is absent rather than a zero row, so the caller
     *         fills the gaps
     */
    @Query(value = """
            SELECT status,
                   COUNT(*),
                   COALESCE(SUM(CAST(total_price AS numeric(19, 2))), CAST(0 AS numeric(19, 2)))
            FROM purchases
            WHERE seller_profile_id = :sellerId
            GROUP BY status
            """, nativeQuery = true)
    List<Object[]> summariseOrdersForSeller(@Param("sellerId") String sellerId);

    /** Orders placed by anyone at this shop since a moment — the "today" counter. */
    long countBySellerProfile_SellerIdAndCreatedAtGreaterThanEqual(String sellerId,
                                                                  LocalDateTime since);

    /**
     * What one shop booked in a window: the value of orders placed in it that were not
     * cancelled.
     *
     * <p>Bucketed on when the order was <em>placed</em>, not when it settled. Orders here
     * are paid in cash on delivery, so settlement trails placement by a day or more —
     * "today's sales" measured on delivery would show a shop nothing for the orders it
     * actually took today, which is the number it opens the dashboard for.
     *
     * <p>Cancelled orders are excluded because they came to nothing. Cent-cast for the
     * same reason as {@link #sumRoundedTotalPriceByStatus}.
     */
    @Query(value = """
            SELECT COALESCE(SUM(CAST(total_price AS numeric(19, 2))), CAST(0 AS numeric(19, 2)))
            FROM purchases
            WHERE seller_profile_id = :sellerId
              AND status <> 'CANCELLED'
              AND created_at >= :from
              AND created_at < :to
            """, nativeQuery = true)
    BigDecimal sumSalesPlacedBetween(@Param("sellerId") String sellerId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    /**
     * A shop's orders and takings per calendar day, for the dashboard chart.
     *
     * <p>Days the shop sold nothing are absent rather than zero rows — a database cannot
     * invent dates it has no data for — so the caller fills the gaps across the window
     * it asked about.
     *
     * @return {@code [date, orderCount, sales]}, oldest first
     */
    @Query(value = """
            SELECT CAST(created_at AS date) AS day,
                   COUNT(*),
                   COALESCE(SUM(CAST(total_price AS numeric(19, 2))), CAST(0 AS numeric(19, 2)))
            FROM purchases
            WHERE seller_profile_id = :sellerId
              AND status <> 'CANCELLED'
              AND created_at >= :from
            GROUP BY CAST(created_at AS date)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> dailySalesForSeller(@Param("sellerId") String sellerId,
                                       @Param("from") LocalDateTime from);

    /**
     * A shop's best sellers, by units moved.
     *
     * <p>Counts only orders the seller accepted. A PENDING order is a request nobody has
     * agreed to yet and a CANCELLED one came to nothing, so counting either would let a
     * shop's own bestseller list be shaped by orders that never happened — the same
     * reasoning as {@link #rankBoughtTogetherWith}.
     *
     * @return {@code [listingUuid, unitsSold, revenue]}, best first
     */
    @Query("SELECT i.listing.uuid, SUM(i.quantity), SUM(i.unitPrice * i.quantity) "
            + "FROM PurchaseItem i "
            + "WHERE i.purchase.sellerProfile.sellerId = :sellerId "
            + "AND i.purchase.status IN :statuses "
            + "GROUP BY i.listing.uuid "
            + "ORDER BY SUM(i.quantity) DESC")
    List<Object[]> rankSellerProductsByUnits(@Param("sellerId") String sellerId,
                                             @Param("statuses") Collection<PurchaseStatus> statuses,
                                             Pageable pageable);

    /**
     * A shop's orders matching a free-text query, for the search box above the order list.
     *
     * <p>Searches the order id, who it is going to, their phone, and the names of the
     * products in it — the four things a seller actually has to hand when a customer
     * rings up about an order. {@code recipientName} rather than the buyer's account
     * name because checkout always records a recipient, and it is the recipient the
     * order is actually for.
     *
     * <p>{@code LEFT JOIN} so an order still matches on its own fields even if a listing
     * in it has since gone, and {@code DISTINCT} so an order whose several items all
     * match is returned once.
     *
     * @param statuses which states to include. Every state when the caller is not
     *                 filtering, rather than a nullable single value — a null enum
     *                 compared against {@code IS NULL} gives Hibernate nothing to infer
     *                 the parameter's type from, and the failure would land at startup.
     */
    @Query(value = """
            SELECT DISTINCT p FROM Purchase p
            LEFT JOIN p.items i
            LEFT JOIN i.listing l
            WHERE p.sellerProfile.sellerId = :sellerId
              AND p.status IN :statuses
              AND (LOWER(CAST(p.uuid AS String)) LIKE :term
                   OR LOWER(p.recipientName) LIKE :term
                   OR LOWER(p.recipientPhone) LIKE :term
                   OR LOWER(l.title) LIKE :term)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p) FROM Purchase p
            LEFT JOIN p.items i
            LEFT JOIN i.listing l
            WHERE p.sellerProfile.sellerId = :sellerId
              AND p.status IN :statuses
              AND (LOWER(CAST(p.uuid AS String)) LIKE :term
                   OR LOWER(p.recipientName) LIKE :term
                   OR LOWER(p.recipientPhone) LIKE :term
                   OR LOWER(l.title) LIKE :term)
            """)
    Page<Purchase> searchSellerOrders(@Param("sellerId") String sellerId,
                                      @Param("statuses") Collection<PurchaseStatus> statuses,
                                      @Param("term") String term,
                                      Pageable pageable);

    long countByStatus(PurchaseStatus status);

    /**
     * Whether any order names this listing.
     *
     * <p>{@code PurchaseItem.listing} is a non-null foreign key, so deleting a listing
     * somebody ordered would either fail outright or take the order line with it.
     * Either way the buyer's history stops saying what they bought.
     */
    boolean existsByItems_Listing_Uuid(UUID listingUuid);

    /**
     * How many distinct people have ever reached this order state — the dashboard's
     * buyer count.
     *
     * <p>Derived from orders rather than from a role, because roles live in Keycloak
     * and no local column marks a profile as a buyer. Counter sales rung up at a
     * seller's till carry no signed-in shopper, so their {@code buyerId} is null;
     * {@code COUNT(DISTINCT)} discards nulls, which keeps every anonymous walk-in
     * from collapsing into one phantom buyer.
     */
    @Query("SELECT COUNT(DISTINCT p.buyerId) FROM Purchase p WHERE p.status = :status")
    long countDistinctBuyersByStatus(@Param("status") PurchaseStatus status);

    /**
     * Order count and lifetime spend for a page of buyers — one query for the whole
     * page rather than two per row.
     *
     * <p>Native and cent-cast for the same reason as
     * {@link #sumRoundedTotalPriceByStatus}: the underlying money column is floating
     * point, and a buyer's total is not something to report with binary drift in it.
     *
     * @return {@code [buyerId, orderCount, totalSpent]}; a buyer with no qualifying
     *         order is absent rather than returned as a zero row
     */
    @Query(value = """
            SELECT buyer_id,
                   COUNT(*),
                   COALESCE(SUM(CAST(total_price AS numeric(19, 2))), CAST(0 AS numeric(19, 2)))
            FROM purchases
            WHERE status = :status AND buyer_id IN (:buyerIds)
            GROUP BY buyer_id
            """, nativeQuery = true)
    List<Object[]> orderStatsForBuyers(@Param("status") String status,
                                       @Param("buyerIds") Collection<String> buyerIds);

    Page<Purchase> findByBuyerIdAndStatus(String buyerId, PurchaseStatus status, Pageable pageable);

    /**
     * Whether this buyer has ever completed an order with this shop — the test for
     * whether they may review it.
     */
    boolean existsByBuyerIdAndSellerProfile_SellerIdAndStatus(String buyerId,
                                                             String sellerId,
                                                             PurchaseStatus status);

    /**
     * Which buyer-and-product pairs actually appear in a completed order — what earns a
     * review its "verified purchase" badge.
     *
     * <p>Stronger than the test above, and deliberately so: the right to review is
     * granted per shop, because buying one size and reviewing the product is ordinary,
     * but the badge claims something narrower — that this person bought this exact thing.
     *
     * <p>Takes both id sets at once so a page of reviews costs one query instead of one
     * per row. The database returns the pairs that genuinely exist, so the cross product
     * of the two lists is narrowed here rather than by the caller.
     *
     * @return {@code [buyerId, listingUuid]} for pairs with a completed order between them
     */
    @Query("SELECT DISTINCT p.buyerId, i.listing.uuid FROM PurchaseItem i "
            + "JOIN i.purchase p "
            + "WHERE p.status = :status "
            + "AND p.buyerId IN :buyerIds "
            + "AND i.listing.uuid IN :listingUuids")
    List<Object[]> completedPurchasePairs(@Param("status") PurchaseStatus status,
                                          @Param("buyerIds") Collection<String> buyerIds,
                                          @Param("listingUuids") Collection<UUID> listingUuids);

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
