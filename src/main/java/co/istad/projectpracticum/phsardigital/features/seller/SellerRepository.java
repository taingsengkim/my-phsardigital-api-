package co.istad.projectpracticum.phsardigital.features.seller;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} backs the admin shop list, whose two filters are
 * both optional — four query methods for the combinations, or one specification.
 */
public interface SellerRepository extends JpaRepository<SellerProfile, String>,
        JpaSpecificationExecutor<SellerProfile> {

    /** Exclusive; for the administrator's own suspension/restore write. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SellerProfile s WHERE s.sellerId = :sellerId")
    Optional<SellerProfile> findByIdForUpdate(@Param("sellerId") String sellerId);

    /**
     * Shared; for trading decisions that read the shop's active flag without changing
     * it. Concurrent orders hold this together, while an administrator's exclusive
     * suspension waits for them — so the two are still ordered, but checkouts for one
     * shop no longer queue behind each other.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM SellerProfile s WHERE s.sellerId = :sellerId")
    Optional<SellerProfile> findByIdForShare(@Param("sellerId") String sellerId);

    /** Shops that may actually trade — the SELLER role survives a suspension, {@code isActive} does not. */
    long countByIsActiveTrue();

    /** Both back the image cleanup in {@code SellerProfileFileListener}. */
    List<SellerProfile> findAllByLogoFile_ObjectName(String objectName);

    List<SellerProfile> findAllByCoverFile_ObjectName(String objectName);
}
