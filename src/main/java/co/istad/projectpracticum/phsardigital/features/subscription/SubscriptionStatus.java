package co.istad.projectpracticum.phsardigital.features.subscription;

public enum SubscriptionStatus {

    /** Paid up and inside its period. */
    ACTIVE,

    /** Its period has run out. Stored, not only computed, so a lapse is visible in the table. */
    EXPIRED,

    /**
     * Ended early by an admin. Kept apart from {@link #EXPIRED} so "we stopped this"
     * and "it ran out" stay tellable apart afterwards.
     */
    CANCELLED
}
