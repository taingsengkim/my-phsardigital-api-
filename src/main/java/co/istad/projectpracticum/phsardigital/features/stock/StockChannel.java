package co.istad.projectpracticum.phsardigital.features.stock;

/** Where a stock movement came from. */
public enum StockChannel {

    /** A marketplace order. */
    ONLINE,

    /** A sale rung up at the seller's own counter. */
    POS,

    /** The seller or an administrator changing the count by hand. */
    MANUAL
}
