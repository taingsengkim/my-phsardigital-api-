package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.NearbySellerResponse;

import java.util.List;

public interface NearbySellerService {

    /**
     * Trading shops near a point, closest first.
     *
     * @param latitude  where the shopper is
     * @param longitude where the shopper is
     * @param radiusKm  how far to look
     * @param limit     how many shops to return
     * @return at most {@code limit} shops, nearest first; empty when nothing within the
     *         radius has a pin on the map
     */
    List<NearbySellerResponse> findNearby(double latitude, double longitude,
                                          double radiusKm, int limit);
}
