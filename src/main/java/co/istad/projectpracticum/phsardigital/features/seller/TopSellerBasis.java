package co.istad.projectpracticum.phsardigital.features.seller;

/**
 * What "top" means. The three orderings pick genuinely different shops — revenue
 * favours expensive low-volume shops, orders favours cheap high-volume ones, rating
 * favours small careful ones — so it is a choice the caller makes rather than a
 * formula hidden in here.
 */
public enum TopSellerBasis {

    /** Value of completed orders. Ranks on it; the figure itself is not published. */
    REVENUE,

    /** Number of completed orders. */
    ORDERS,

    /** Average review score, among shops with enough reviews to mean anything. */
    RATING
}
