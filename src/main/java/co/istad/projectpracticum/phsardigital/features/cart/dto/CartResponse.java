package co.istad.projectpracticum.phsardigital.features.cart.dto;
import java.util.List;
import java.util.UUID;
public record CartResponse(
        UUID uuid,
        String sellerId,
        List<CartItemResponse> items,
        Double totalPrice
) {
}