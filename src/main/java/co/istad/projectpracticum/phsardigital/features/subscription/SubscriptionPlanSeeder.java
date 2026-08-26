package co.istad.projectpracticum.phsardigital.features.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Puts the original three plans in the table the first time the application starts
 * against an empty catalogue.
 *
 * <p>Only when it is <em>completely</em> empty. Seeding per-code instead would
 * resurrect a plan an admin deliberately retired on the next restart, and retiring a
 * plan is {@code deactivate}, not delete — so an empty table means a fresh database,
 * not an emptied one.
 *
 * <p>The values match what the enum held before the catalogue moved into the
 * database, so an existing {@code seller_subscriptions.plan} of {@code BASIC} keeps
 * resolving to exactly the plan it always did.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanSeeder implements ApplicationRunner {

    private final SubscriptionPlanRepository planRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (planRepository.count() > 0) {
            return;
        }

        List<SubscriptionPlan> defaults = List.of(
                plan("BASIC", "Basic", "5.00", 30, 20, 1),
                plan("STANDARD", "Standard", "12.00", 30, 100, 2),
                plan("PREMIUM", "Premium", "25.00", 30,
                        SubscriptionPlan.UNLIMITED_LISTINGS, 3));

        planRepository.saveAll(defaults);
        log.info("Seeded {} subscription plans into an empty catalogue", defaults.size());
    }

    private static SubscriptionPlan plan(String code, String displayName, String priceUsd,
                                         int durationDays, int listingLimit, int sortOrder) {
        SubscriptionPlan plan = new SubscriptionPlan(code);
        plan.setDisplayName(displayName);
        plan.setPriceUsd(new BigDecimal(priceUsd));
        plan.setDurationDays(durationDays);
        plan.setListingLimit(listingLimit);
        plan.setActive(true);
        plan.setSortOrder(sortOrder);
        return plan;
    }
}
