package co.istad.projectpracticum.phsardigital.features.admin.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A point-in-time view of the marketplace metrics shown on the admin dashboard.
 *
 * <p>Current gauges (active sellers, publicly available listings, pending seller
 * applications, and active subscriptions) sit beside lifetime completed-order
 * figures. The API groups them by domain so adding one card does not keep extending
 * a fragile positional list of unrelated values.
 *
 * <p>{@link PurchaseSummary#completedGmv()} is gross merchandise value: money paid
 * by buyers to sellers for completed orders. It is not platform revenue.
 *
 * @param asOf the absolute instant at which this database snapshot was evaluated
 */
public record AdminDashboardSummaryResponse(
        UserSummary users,
        SellerSummary sellers,
        ListingSummary listings,
        ApplicationSummary applications,
        PurchaseSummary orders,
        SubscriptionSummary subscriptions,
        Instant asOf
) {

    public AdminDashboardSummaryResponse {
        Objects.requireNonNull(users, "users is required");
        Objects.requireNonNull(sellers, "sellers is required");
        Objects.requireNonNull(listings, "listings is required");
        Objects.requireNonNull(applications, "applications is required");
        Objects.requireNonNull(orders, "orders is required");
        Objects.requireNonNull(subscriptions, "subscriptions is required");
        Objects.requireNonNull(asOf, "asOf is required");
    }

    /** Local user profiles provisioned in this application. */
    public record UserSummary(long total) {
        public UserSummary {
            requireNonNegative(total, "users.total");
        }
    }

    /** All seller profiles and the subset currently allowed to trade. */
    public record SellerSummary(long total, long active) {
        public SellerSummary {
            requireNonNegative(total, "sellers.total");
            requireNonNegative(active, "sellers.active");
            requireNotGreater(active, total, "sellers.active", "sellers.total");
        }
    }

    /**
     * All listings and the subset a buyer can currently see and purchase.
     * A listing owned by a suspended seller is not publicly available even when its
     * own status remains {@code ACTIVE}.
     */
    public record ListingSummary(long total, long publiclyAvailable) {
        public ListingSummary {
            requireNonNegative(total, "listings.total");
            requireNonNegative(publiclyAvailable, "listings.publiclyAvailable");
            requireNotGreater(publiclyAvailable, total,
                    "listings.publiclyAvailable", "listings.total");
        }
    }

    /** Seller applications currently waiting for an admin decision. */
    public record ApplicationSummary(long pending) {
        public ApplicationSummary {
            requireNonNegative(pending, "applications.pending");
        }
    }

    /** Lifetime completed-order metrics. */
    public record PurchaseSummary(long completed, Money completedGmv) {
        public PurchaseSummary {
            requireNonNegative(completed, "orders.completed");
            Objects.requireNonNull(completedGmv, "orders.completedGmv is required");
        }
    }

    /** A currency-aware monetary value normalized to two decimal places. */
    public record Money(BigDecimal amount, String currencyCode) {
        public Money {
            Objects.requireNonNull(amount, "amount is required");
            Objects.requireNonNull(currencyCode, "currencyCode is required");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("amount cannot be negative");
            }

            amount = amount.setScale(2, RoundingMode.HALF_UP);
            currencyCode = currencyCode.trim().toUpperCase(Locale.ROOT);
            if (!currencyCode.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException(
                        "currencyCode must be a three-letter ISO 4217 code");
            }
        }
    }

    /** Active subscriptions with a stable, ordered plan breakdown. */
    public record SubscriptionSummary(
            long active,
            List<PlanSubscriptionCount> byPlan
    ) {
        public SubscriptionSummary(List<PlanSubscriptionCount> byPlan) {
            this(total(byPlan), byPlan);
        }

        public SubscriptionSummary {
            requireNonNegative(active, "subscriptions.active");
            byPlan = List.copyOf(Objects.requireNonNull(
                    byPlan, "subscriptions.byPlan is required"));
            if (active != total(byPlan)) {
                throw new IllegalArgumentException(
                        "subscriptions.active must equal the sum of subscriptions.byPlan");
            }
        }

        private static long total(List<PlanSubscriptionCount> byPlan) {
            Objects.requireNonNull(byPlan, "subscriptions.byPlan is required");
            return byPlan.stream()
                    .mapToLong(PlanSubscriptionCount::count)
                    .reduce(0L, Math::addExact);
        }
    }

    /** A plan's public code, display label, and currently active subscriptions. */
    public record PlanSubscriptionCount(
            String code,
            String displayName,
            long count
    ) {
        public PlanSubscriptionCount {
            code = requireText(code, "plan code");
            displayName = requireText(displayName, "plan displayName");
            requireNonNegative(count, "plan subscription count");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private static void requireNotGreater(long value, long maximum,
                                          String valueName, String maximumName) {
        if (value > maximum) {
            throw new IllegalArgumentException(valueName + " cannot exceed " + maximumName);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return trimmed;
    }
}
