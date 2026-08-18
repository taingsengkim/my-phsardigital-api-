package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.TopSellerResponse;

import java.util.List;

public interface TopSellerService {

    /**
     * The Top Sellers board.
     *
     * @param basis  what to rank on
     * @param period how far back to count
     * @param limit  how many shops to return
     */
    List<TopSellerResponse> getTopSellers(TopSellerBasis basis, TopSellerPeriod period, int limit);
}
