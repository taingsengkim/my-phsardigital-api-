package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A file used as a listing's thumbnail or gallery image. Both columns are
 * {@code NOT NULL}, so the file cannot be deleted while a listing depends on it.
 */
@Component
@RequiredArgsConstructor
public class ListingFileReferences implements FileReferenceCheck {

    private final ListingRepository listingRepository;

    @Override
    public String referenceName() {
        return "a listing image";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return listingRepository.existsByThumbnailFile_ObjectName(objectName)
                || listingRepository.existsByImages_File_ObjectName(objectName);
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
