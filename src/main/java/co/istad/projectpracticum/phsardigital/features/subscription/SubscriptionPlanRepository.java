package co.istad.projectpracticum.phsardigital.features.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {

    /** The pricing page: what a seller can sign up for today. */
    List<SubscriptionPlan> findAllByActiveTrueOrderBySortOrderAsc();

    /** The admin catalogue and the dashboard breakdown: retired plans included. */
    List<SubscriptionPlan> findAllByOrderBySortOrderAsc();
}
