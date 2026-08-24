package co.istad.projectpracticum.phsardigital.features.purchases;

/** How the order was taken. Both channels sell the same stock and report as one. */
public enum PurchaseChannel {

    /** Placed by a buyer through the marketplace, with a delivery address. */
    ONLINE,

    /** Rung up at the seller's own counter, settled on the spot. */
    POS
}
