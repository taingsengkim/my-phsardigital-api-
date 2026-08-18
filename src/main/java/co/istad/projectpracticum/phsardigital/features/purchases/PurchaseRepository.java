package co.istad.projectpracticum.phsardigital.features.purchases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * @return the sum, or zero when no order qualifies ({@code SUM} over no rows is null)
     */
    @Query("SELECT COALESCE(SUM(p.totalPrice), 0) FROM Purchase p WHERE p.status = :status")
    Double sumTotalPriceByStatus(@Param("status") PurchaseStatus status);

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
}