package co.istad.projectpracticum.phsardigital.features.cart.dto;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileSummaryResponse;

import java.util.List;
import java.util.UUID;

/**
 * One shop's basket. Carts are per-vendor, so a buyer holds several of these at once and
 * each needs enough of the shop to head its own section.
 *
 * @param sellerProfile the shop this basket belongs to. Replaces the bare
 *                      {@code sellerId}, which is still here as
 *                      {@code sellerProfile.sellerId}
 */
public record CartResponse(
        UUID uuid,
        SellerProfileSummaryResponse sellerProfile,
        List<CartItemResponse> items,
        BigDecimal totalPrice
) {
}
