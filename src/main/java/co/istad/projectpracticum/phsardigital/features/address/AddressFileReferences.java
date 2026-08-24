package co.istad.projectpracticum.phsardigital.features.address;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A file used as a landmark photo on a saved delivery address. */
@Component
@RequiredArgsConstructor
public class AddressFileReferences implements FileReferenceCheck {

    private final AddressRepository addressRepository;

    @Override
    public String referenceName() {
        return "an address landmark photo";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return !addressRepository.findAllByPhotos_File_ObjectName(objectName).isEmpty();
    }
}
