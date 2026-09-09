package co.istad.projectpracticum.phsardigital.features.purchases;

/**
 * The events an order's timeline can show.
 *
 * <p>Exactly four, because the timeline is derived from the four moments an order
 * actually records — {@code createdAt}, {@code confirmedAt}, {@code completedAt},
 * {@code cancelledAt}. There is no audit table behind it, so nothing finer can be
 * reported honestly: an admin editing a note or a courier collecting cash leaves no
 * trace this can read.
 *
 * <p>Adding a payment event here would be a lie of the same kind. Online orders settle
 * in cash on delivery and the platform never sees the money move.
 */
public enum PurchaseActivityAction {

    /** The buyer placed the order; it is waiting on the shop. */
    ORDER_PLACED,

    /** The shop accepted it and stock was reserved. */
    ORDER_CONFIRMED,

    /** Delivered and settled. */
    ORDER_COMPLETED,

    /** Called off, by either side. */
    ORDER_CANCELLED
}
