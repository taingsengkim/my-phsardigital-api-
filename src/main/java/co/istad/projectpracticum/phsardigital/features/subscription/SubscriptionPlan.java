package co.istad.projectpracticum.phsardigital.features.subscription;

import java.math.BigDecimal;

/**
 * The plans a seller can be on, and what each one allows.
 *
 * <p>Deliberately static: there is no billing integration yet, so the catalogue
 * lives in code rather than in a table an admin edits. What matters is that the
 * <em>gate</em> is real — {@code SubscriptionService} refuses to let an
 * unsubscribed seller post or chat — so swapping this enum for a payment provider
 * later is a change to how a subscription is granted, not to who is allowed to do
 * what.
 */
public enum SubscriptionPlan {

    BASIC("Basic", new BigDecimal("5.00"), 30, 20),
    STANDARD("Standard", new BigDecimal("12.00"), 30, 100),
    // -1 spelled out rather than UNLIMITED_LISTINGS: an enum constant's arguments
    // cannot forward-reference a field declared below it.
    PREMIUM("Premium", new BigDecimal("25.00"), 30, -1);

    /** Sentinel for {@link #listingLimit}: no cap on how many listings a shop keeps. */
    public static final int UNLIMITED_LISTINGS = -1;

    private final String displayName;
    private final BigDecimal priceUsd;
    private final int durationDays;
    private final int listingLimit;

    SubscriptionPlan(String displayName, BigDecimal priceUsd, int durationDays, int listingLimit) {
        this.displayName = displayName;
        this.priceUsd = priceUsd;
        this.durationDays = durationDays;
        this.listingLimit = listingLimit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getPriceUsd() {
        return priceUsd;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public int getListingLimit() {
        return listingLimit;
    }

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
}
