package co.istad.projectpracticum.phsardigital.features.purchases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}