package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseItemResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PurchaseMapper {

    public PurchaseResponse toResponse(Purchase p) {
        List<PurchaseItemResponse> items = p.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new PurchaseResponse(
                p.getUuid(),
                p.getBuyerId(),
                p.getSellerProfile().getSellerId(),
                p.getSellerProfile().getBusinessName(),
                p.getTotalPrice(),
                p.getStatus(),
                p.getShippingAddress(),
                p.getNote(),
                items,
                p.getCreatedAt()
        );
    }

    private PurchaseItemResponse toItemResponse(PurchaseItem it) {
        return new PurchaseItemResponse(
                it.getListing().getUuid(),
                it.getListing().getTitle(),
                it.getQuantity(),
                it.getUnitPrice(),
                it.getUnitPrice() * it.getQuantity()
        );
    }
}