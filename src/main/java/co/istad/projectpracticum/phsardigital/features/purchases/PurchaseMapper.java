package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.DeliveryPhotoResponse;
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
    private final FileUploadService fileUploadService;

    public PurchaseResponse toResponse(Purchase p) {
        List<PurchaseItemResponse> items = p.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        // Purchase stores the buyer as a bare id, so the name and phone need a lookup.
        // One per order: a page of orders costs a query per distinct buyer, which is
        // worth revisiting if order history ever gets long, but repeated buyers on the
        // same page are served from the persistence context.
        UserProfile buyer = userProfileRepository.findById(p.getBuyerId()).orElse(null);

        // The recipient copied off the delivery address wins over the account holder:
        // people order to a parent's or a colleague's address, and it is the recipient
        // the courier has to ask for.
        String name = p.getRecipientName() != null ? p.getRecipientName()
                : (buyer == null ? null : buyer.getFullName());
        String phone = p.getRecipientPhone() != null ? p.getRecipientPhone()
                : (buyer == null ? null : buyer.getPhone());

        return new PurchaseResponse(
                p.getUuid(),
                p.getBuyerId(),
                name,
                phone,
                p.getSellerProfile().getSellerId(),
                p.getSellerProfile().getBusinessName(),
                p.getTotalPrice(),
                p.getStatus(),
                p.getShippingAddress(),
                toDeliveryPhotos(p),
                p.getNote(),
                items,
                p.getCreatedAt()
        );
    }

    /** The landmark shots the buyer saved, so the courier gets the door, not just the line. */
    private List<DeliveryPhotoResponse> toDeliveryPhotos(Purchase p) {
        return p.getDeliveryPhotos().stream()
                .map(photo -> new DeliveryPhotoResponse(
                        fileUploadService.getPreviewUrl(photo.getFile()),
                        photo.getCaption()))
                .toList();
    }

    private PurchaseItemResponse toItemResponse(PurchaseItem it) {
        return new PurchaseItemResponse(
                it.getListing().getUuid(),
                it.getListing().getTitle(),
                it.getQuantity(),
                it.getUnitFullPrice(),
                it.getUnitPrice(),
                it.getUnitPrice() * it.getQuantity()
        );
    }
}
