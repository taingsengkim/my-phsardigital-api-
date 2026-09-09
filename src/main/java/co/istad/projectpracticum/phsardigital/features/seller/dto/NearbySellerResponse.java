package co.istad.projectpracticum.phsardigital.features.seller.dto;

import java.math.BigDecimal;

/**
 * A shop on the "near me" list.
 *
 * <p>Carries the pin and the Google Maps link as well as the distance, so a client can
 * drop every result on a map and offer directions without a follow-up call per shop.
 * Only shops that have a pin can appear here at all — a shop that has never set one is
 * not at an unknown distance, it is simply not on this list.
 *
 * @param distanceKm    straight-line distance from the point that was asked about,
 *                      rounded to ten metres — it is a sort key and a label, not a route
 * @param averageRating over the shop's whole history, null when nobody has reviewed it
 */
public record NearbySellerResponse(
        String sellerId,
        String businessName,
        String logoUri,
        String address,
        String city,
        String province,
        BigDecimal latitude,
        BigDecimal longitude,
        String googleMapUrl,
        double distanceKm,
        Double averageRating,
        long reviewCount
) {
}
