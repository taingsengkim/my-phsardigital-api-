package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A file still shown as a delivery landmark on an order. */
@Component
@RequiredArgsConstructor
public class PurchaseFileReferences implements FileReferenceCheck {

    private final PurchaseRepository purchaseRepository;

    @Override
    public String referenceName() {
        return "an order delivery photo";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return !purchaseRepository.findAllByDeliveryPhotos_File_ObjectName(objectName).isEmpty();
    }
}
