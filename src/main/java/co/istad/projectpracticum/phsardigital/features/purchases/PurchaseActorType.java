package co.istad.projectpracticum.phsardigital.features.purchases;

/**
 * Who did the thing on an order's timeline.
 *
 * <p>{@link #UNKNOWN} is a real answer here, not a placeholder to be tidied away later.
 * {@code PurchaseServiceImpl#cancel} lets either the buyer or the seller call an order
 * off and records only that it happened, so a cancellation genuinely cannot say who is
 * responsible. Naming a party would be inventing evidence in the one view an
 * administrator consults when the two sides disagree.
 */
public enum PurchaseActorType {

    BUYER,
    SELLER,

    /** Nothing recorded who acted. */
    UNKNOWN
}
