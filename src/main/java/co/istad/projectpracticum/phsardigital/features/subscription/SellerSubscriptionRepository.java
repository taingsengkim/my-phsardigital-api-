package co.istad.projectpracticum.phsardigital.features.subscription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SellerSubscriptionRepository extends JpaRepository<SellerSubscription, String> {

    /**
     * How many shops are on each plan right now, judged against the clock as well as
     * the stored status — mirroring {@link SellerSubscription#isCurrentlyActive()},
     * since nothing moves a lapsed row to {@code EXPIRED} until somebody reads it.
     *
     * @return {@code [planCode, Long]} per plan with live subscriptions; plans nobody
     *         is on are absent rather than zero
     */
    @Query("SELECT s.planCode, COUNT(s) FROM SellerSubscription s "
            + "WHERE s.status = :status AND s.expiresAt > :now "
            + "GROUP BY s.planCode")
    List<Object[]> countActiveByPlan(@Param("status") SubscriptionStatus status,
                                     @Param("now") LocalDateTime now);

    Page<SellerSubscription> findByStatus(SubscriptionStatus status, Pageable pageable);

    Page<SellerSubscription> findByPlanCode(String planCode, Pageable pageable);

    Page<SellerSubscription> findByStatusAndPlanCode(SubscriptionStatus status,
                                                     String planCode,
                                                     Pageable pageable);

    /** Whether retiring a plan would strand anybody currently on it. */
    boolean existsByPlanCodeAndStatus(String planCode, SubscriptionStatus status);
}
