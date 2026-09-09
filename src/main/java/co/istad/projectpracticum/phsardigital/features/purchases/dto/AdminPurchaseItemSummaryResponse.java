package co.istad.projectpracticum.phsardigital.features.purchases.dto;

/**
 * What is in an order, condensed to what a table row can show: one product, and a count
 * of how many others are hiding behind it.
 *
 * <p>The full lines are on the detail route. A list endpoint that serialised every item
 * of every order would send a page of orders as several hundred products so a table
 * could render one thumbnail each.
 *
 * @param lineCount     how many distinct products, which is what "+2 more" counts
 * @param unitCount     how many units in total, which is what a picker actually packs —
 *                      an order of one product times twelve is not a one-item order
 * @param firstTitle    frozen at nothing: read live off the listing, like the thumbnail,
 *                      since both exist only to identify the product at a glance
 * @param hasMoreItems  convenience for the badge, true when {@code lineCount > 1}
 */
public record AdminPurchaseItemSummaryResponse(
        int lineCount,
        int unitCount,
        String firstTitle,
        String firstThumbnailUrl,
        boolean hasMoreItems
) {
}
