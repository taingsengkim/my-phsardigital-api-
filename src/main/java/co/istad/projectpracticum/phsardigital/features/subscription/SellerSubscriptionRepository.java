package co.istad.projectpracticum.phsardigital.features.subscription;

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
     * @return {@code [SubscriptionPlan, Long]} per plan with live subscriptions;
     *         plans nobody is on are absent rather than zero
     */
    @Query("SELECT s.plan, COUNT(s) FROM SellerSubscription s "
            + "WHERE s.status = :status AND s.expiresAt > :now "
            + "GROUP BY s.plan")
    List<Object[]> countActiveByPlan(@Param("status") SubscriptionStatus status,
                                     @Param("now") LocalDateTime now);
}
