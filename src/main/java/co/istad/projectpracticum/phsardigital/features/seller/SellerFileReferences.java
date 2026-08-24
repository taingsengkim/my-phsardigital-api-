package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import co.istad.projectpracticum.phsardigital.features.seller.application.SellerApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A file used as a shop logo or cover, or as the logo on a pending application. */
@Component
@RequiredArgsConstructor
public class SellerFileReferences implements FileReferenceCheck {

    private final SellerRepository sellerRepository;
    private final SellerApplicationRepository applicationRepository;

    @Override
    public String referenceName() {
        return "a shop logo or cover";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return !sellerRepository.findAllByLogoFile_ObjectName(objectName).isEmpty()
                || !sellerRepository.findAllByCoverFile_ObjectName(objectName).isEmpty()
                || !applicationRepository.findAllByLogoFile_ObjectName(objectName).isEmpty();
    }
}
