package co.istad.projectpracticum.phsardigital.features.purchases.dto;

public record CheckoutRequest(
        String shippingAddress,
        String note
) {
}