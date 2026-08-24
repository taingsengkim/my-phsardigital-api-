package co.istad.projectpracticum.phsardigital.features.address.listener;

import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.address.Address;
import co.istad.projectpracticum.phsardigital.features.address.AddressPhoto;
import co.istad.projectpracticum.phsardigital.features.address.AddressRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drops landmark photos whose underlying file is being deleted, so removing a file
 * never trips the foreign key on {@code address_photos.file_id}.
 *
 * <p>The row goes rather than being blanked: a landmark photo with no image left is
 * only a caption pointing at nothing, and leaving it would put an empty tile in front
 * of the courier. Orders keep their own copies, so this never rewrites a past delivery.
 *
 * <p>The event arrives before the file row is deleted, so the photo still points at it
 * here — matching is by object name rather than by a reference that is not null yet.
 */
@Component
@RequiredArgsConstructor
public class AddressFileListener {

    private final AddressRepository addressRepository;

    @EventListener
    @Transactional
    public void handle(FileDeletedEvent event) {
        List<Address> addresses = addressRepository
                .findAllByPhotos_File_ObjectName(event.objectName());
        for (Address address : addresses) {
            address.getPhotos().removeIf(photo -> pointsAt(photo, event.objectName()));
        }
    }

    private static boolean pointsAt(AddressPhoto photo, String objectName) {
        return photo.getFile() != null
                && objectName.equals(photo.getFile().getObjectName());
    }
}
