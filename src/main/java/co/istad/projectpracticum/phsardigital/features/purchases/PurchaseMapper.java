package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
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
        UserProfile buyer = p.getBuyerId() == null
                ? null
                : userProfileRepository.findById(p.getBuyerId()).orElse(null);

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
                previewUrl(p.getSellerProfile().getLogoFile()),
                p.getTotalPrice(),
                p.getStatus(),
                p.getChannel(),
                p.getShippingAddress(),
                p.getDeliveryLatitude(),
                p.getDeliveryLongitude(),
                toDeliveryPhotos(p),
                p.getNote(),
                items,
                p.getCreatedAt(),
                p.getConfirmedAt(),
                p.getCompletedAt(),
                p.getCancelledAt()
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

    /**
     * The title stays as the order recorded it, while the slug and thumbnail are read
     * live off the listing. The difference is deliberate: what was bought is history and
     * must not change, but a link and a picture only exist to reach the product page as
     * it is now, and a stale slug is just a dead link.
     */
    private PurchaseItemResponse toItemResponse(PurchaseItem it) {
        Listing listing = it.getListing();
        return new PurchaseItemResponse(
                listing.getUuid(),
                listing.getTitle(),
                listing.getSlug(),
                previewUrl(listing.getThumbnailFile()),
                it.getQuantity(),
                it.getUnitFullPrice(),
                it.getUnitPrice(),
                Money.multiply(it.getUnitPrice(), it.getQuantity())
        );
    }

    /** Null-safe: a shop with no logo, or a product with no photo, is ordinary. */
    private String previewUrl(FileUpload file) {
        return file == null ? null : fileUploadService.getPreviewUrl(file);
    }
}
