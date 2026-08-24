package co.istad.projectpracticum.phsardigital.features.purchases.listener;

import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.purchases.Purchase;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseDeliveryPhoto;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drops delivery photos whose underlying file is being deleted, so removing a file
 * never trips the foreign key on {@code purchase_delivery_photos.file_id}.
 *
 * <p>Only the image goes. The order's address text, recipient and items are copies and
 * are untouched, so a past delivery still says where it went.
 */
@Component
@RequiredArgsConstructor
public class PurchaseFileListener {

    private final PurchaseRepository purchaseRepository;

    @EventListener
    @Transactional
    public void handle(FileDeletedEvent event) {
        List<Purchase> purchases = purchaseRepository
                .findAllByDeliveryPhotos_File_ObjectName(event.objectName());
        for (Purchase purchase : purchases) {
            purchase.getDeliveryPhotos().removeIf(photo -> pointsAt(photo, event.objectName()));
        }
    }

    private static boolean pointsAt(PurchaseDeliveryPhoto photo, String objectName) {
        return photo.getFile() != null
                && objectName.equals(photo.getFile().getObjectName());
    }
}
