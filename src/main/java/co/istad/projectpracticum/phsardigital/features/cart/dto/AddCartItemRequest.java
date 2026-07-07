package co.istad.projectpracticum.phsardigital.features.cart.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record AddCartItemRequest(
        @NotNull UUID listingUuid,
        @NotNull @Min(1) Integer quantity
) {
}