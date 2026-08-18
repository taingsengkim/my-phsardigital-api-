package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseItemResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseResponse;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PurchaseMapper {

    private final UserProfileRepository userProfileRepository;

    public PurchaseResponse toResponse(Purchase p) {
        List<PurchaseItemResponse> items = p.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        // Purchase stores the buyer as a bare id, so the name and phone need a lookup.
        // One per order: a page of orders costs a query per distinct buyer, which is
        // worth revisiting if order history ever gets long, but repeated buyers on the
        // same page are served from the persistence context.
        UserProfile buyer = userProfileRepository.findById(p.getBuyerId()).orElse(null);

        return new PurchaseResponse(
                p.getUuid(),
                p.getBuyerId(),
                buyer == null ? null : buyer.getFullName(),
                buyer == null ? null : buyer.getPhone(),
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
