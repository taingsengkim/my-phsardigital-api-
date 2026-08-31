package co.istad.projectpracticum.phsardigital.features.listings;

public enum ListingStatus {
    DRAFT, ACTIVE, SOLD_OUT, ARCHIVED,

    /**
     * Taken down by an admin, pending review. A seller can neither set nor leave it —
     * every other status below is theirs to choose, so a moderation state they could
     * simply PATCH back to {@code ACTIVE} would not be moderation at all.
     */
    SUSPENDED,

    /**
     * Taken down by an admin for good.
     *
     * <p>Kept apart from {@link #SUSPENDED} so "we are looking at this" and "this is
     * not coming back" stay tellable apart — to the seller, and to the next admin.
     * Still a status rather than a row deletion: order history points at listings with
     * a non-null foreign key, so erasing one would take the orders with it.
     */
    REMOVED
}
