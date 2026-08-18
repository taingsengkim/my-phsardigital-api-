package co.istad.projectpracticum.phsardigital.features.listings;

public enum ListingStatus {
    DRAFT, ACTIVE, SOLD_OUT, ARCHIVED,

    /**
     * Taken down by an admin. The only status a seller can neither set nor leave —
     * every other one is theirs to choose, so a moderation state they could simply
     * PATCH back to {@code ACTIVE} would not be moderation at all.
     */
    SUSPENDED
}
