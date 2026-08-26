package co.istad.projectpracticum.phsardigital.features.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String>,
        JpaSpecificationExecutor<UserProfile> {

    /**
     * How many buyer accounts sit in each standing.
     *
     * <p>A buyer is an account with no shop. There is no buyer role to filter on —
     * roles live in Keycloak — so the marketplace's own definition is the absence of a
     * {@code SellerProfile}, which is the same rule the admin buyer list pages over.
     *
     * @return {@code [status, count]} per standing; a standing nobody holds is absent
     */
    @Query("SELECT u.status, COUNT(u) FROM UserProfile u "
            + "WHERE NOT EXISTS (SELECT 1 FROM SellerProfile s WHERE s.sellerId = u.id) "
            + "GROUP BY u.status")
    List<Object[]> countBuyersByStatus();

    /**
     * Serializes buyer-scoped commerce operations that have no child row to lock yet,
     * such as first-cart creation and replay-safe checkout creation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserProfile u WHERE u.id = :userId")
    Optional<UserProfile> findByIdForCommerceLock(@Param("userId") String userId);

    Page<UserProfile> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email,
            String fullName,
            Pageable pageable
    );

    List<UserProfile> findAllByAvatarFile_ObjectName(String objectName);
}
