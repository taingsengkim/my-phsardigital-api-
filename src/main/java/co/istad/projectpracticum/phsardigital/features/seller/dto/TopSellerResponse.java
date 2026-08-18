package co.istad.projectpracticum.phsardigital.features.seller.dto;

/**
 * A shop's entry on the Top Sellers board.
 *
 * <p>There is deliberately no revenue figure, even when revenue is what the board was
 * ranked by. How much a shop takes is its own business, and this endpoint is public —
 * publishing it would hand every seller their competitors' turnover. Order count is
 * published because marketplaces already show it and a seller advertises it happily.
 *
 * @param rank          1-based position on this board
 * @param averageRating null when the shop has no reviews in the period
 */
public record TopSellerResponse(
        int rank,
        String sellerId,
        String businessName,
        String logoUri,
        String city,
        String province,
        Double averageRating,
        long reviewCount,
        long completedOrders
) {
}
