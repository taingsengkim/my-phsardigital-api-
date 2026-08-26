package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A plan a seller can be on, and what it allows.
 *
 * <p>Was an enum in code. It is a table now so an admin can change a price or a
 * listing cap without a redeploy — but {@link #code} is still the identity, and
 * {@code seller_subscriptions.plan} still stores that code as a plain string. That is
 * deliberate: the existing column keeps its existing values, so moving the catalogue
 * into the database needed no data migration and no foreign key.
 *
 * <p>Plans are never hard-deleted, because historical subscriptions name them.
 * {@link #active} takes a plan off the pricing page while leaving it resolvable for
 * everyone already on it.
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan extends BasedEntity {

    /** Sentinel for {@link #listingLimit}: no cap on how many listings a shop keeps. */
    public static final int UNLIMITED_LISTINGS = -1;

    public SubscriptionPlan(String code) {
        this.code = code;
    }

    /** Stable identifier, e.g. {@code BASIC}. Never renamed — subscriptions store it. */
    @Id
    @Column(length = 30)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "price_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    /** {@link #UNLIMITED_LISTINGS} for no cap. */
    @Column(name = "listing_limit", nullable = false)
    private int listingLimit;

    /** Whether the plan is offered to new subscribers. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Where the plan sits on the pricing page, cheapest first by convention. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public boolean hasUnlimitedListings() {
        return listingLimit == UNLIMITED_LISTINGS;
    }

    /**
     * @param currentListings how many listings the shop already keeps
     * @return whether one more fits inside this plan
     */
    public boolean allowsAnotherListing(long currentListings) {
        return hasUnlimitedListings() || currentListings < listingLimit;
    }

    /** Null rather than the sentinel, so clients render "Unlimited" without knowing it. */
    public Integer listingLimitOrNull() {
        return hasUnlimitedListings() ? null : listingLimit;
    }
}
