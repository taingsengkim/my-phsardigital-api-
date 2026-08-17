package co.istad.projectpracticum.phsardigital.features.subscription;

public enum SubscriptionStatus {

    /** Paid up and inside its period. */
    ACTIVE,

    /** Its period has run out. Stored, not only computed, so a lapse is visible in the table. */
    EXPIRED
}
