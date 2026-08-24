package co.istad.projectpracticum.phsardigital.features.stock;

/** Why stock moved. Paired with {@link StockChannel} to answer "sold where, and how". */
public enum StockMovementReason {

    /** Stock set when the listing was created. */
    INITIAL,

    /** Goods left the shelf against an order or a counter sale. */
    SALE,

    /** A sale was undone and the goods came back. */
    CANCEL,

    /** New goods arrived. */
    RESTOCK,

    /** A correction — a recount, breakage, or fixing an earlier mistake. */
    ADJUST
}
