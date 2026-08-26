package co.istad.projectpracticum.phsardigital.features.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // ------------------------------------------------------------------
    // Flat compatibility view.
    //
    // The admin dashboard client was written against the original flat
    // response and reads one key per card. These derived properties
    // republish the numbers above under those old names so the client keeps
    // working; none of them is separate state, and none is read back on
    // deserialization. Delete this block once the client reads the grouped
    // fields — nothing on the server depends on it.
    // ------------------------------------------------------------------

    @JsonProperty(value = "totalUsers", access = JsonProperty.Access.READ_ONLY)
    public long totalUsers() {
        return users.total();
    }

    @JsonProperty(value = "totalBuyers", access = JsonProperty.Access.READ_ONLY)
    public long totalBuyers() {
        return users.buyers();
    }

    @JsonProperty(value = "totalSellers", access = JsonProperty.Access.READ_ONLY)
    public long totalSellers() {
        return sellers.total();
    }

    @JsonProperty(value = "activeSellers", access = JsonProperty.Access.READ_ONLY)
    public long activeSellers() {
        return sellers.active();
    }

    @JsonProperty(value = "totalListings", access = JsonProperty.Access.READ_ONLY)
    public long totalListings() {
        return listings.total();
    }

    @JsonProperty(value = "activeListings", access = JsonProperty.Access.READ_ONLY)
    public long activeListings() {
        return listings.publiclyAvailable();
    }

    @JsonProperty(value = "pendingApplications", access = JsonProperty.Access.READ_ONLY)
    public long pendingApplications() {
        return applications.pending();
    }

    @JsonProperty(value = "pendingDocuments", access = JsonProperty.Access.READ_ONLY)
    public long pendingDocuments() {
        return applications.pendingDocuments();
    }

    @JsonProperty(value = "totalTransactions", access = JsonProperty.Access.READ_ONLY)
    public long totalTransactions() {
        return orders.completed();
    }

    /**
     * Published under the client's {@code totalRevenue} name, but the value is
     * {@link PurchaseSummary#completedGmv()} — merchandise value that moved between
     * buyers and sellers, not money the platform earned.
     */
    @JsonProperty(value = "totalRevenue", access = JsonProperty.Access.READ_ONLY)
    public BigDecimal totalRevenue() {
        return orders.completedGmv().amount();
    }

    @JsonProperty(value = "activeSubscriptions", access = JsonProperty.Access.READ_ONLY)
    public long activeSubscriptions() {
        return subscriptions.active();
    }

    /** The plan breakdown keyed by plan code, the shape the old client charts. */
    @JsonProperty(value = "activeSubscriptionsByPlan", access = JsonProperty.Access.READ_ONLY)
    public Map<String, Long> activeSubscriptionsByPlan() {
        Map<String, Long> byCode = new LinkedHashMap<>();
        for (PlanSubscriptionCount plan : subscriptions.byPlan()) {
            byCode.put(plan.code(), plan.count());
        }
        return byCode;
    }

    /**
     * Local user profiles provisioned in this application, and how many of them have
     * actually bought something.
     *
     * <p>{@code buyers} is deliberately not bounded by {@code total}: it is counted
     * from completed orders, and an order's buyer is a Keycloak subject that may have
     * no local profile row yet. Treating that as a violation would fail the whole
     * dashboard over a harmless data lag.
     */
    public record UserSummary(long total, long buyers) {
        public UserSummary {
            requireNonNegative(total, "users.total");
            requireNonNegative(buyers, "users.buyers");
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

    /**
     * Seller applications currently waiting for an admin decision, and the documents
     * uploaded against them. One application can carry several documents, so
     * {@code pendingDocuments} is not bounded by {@code pending} either way.
     */
    public record ApplicationSummary(long pending, long pendingDocuments) {
        public ApplicationSummary {
            requireNonNegative(pending, "applications.pending");
            requireNonNegative(pendingDocuments, "applications.pendingDocuments");
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
