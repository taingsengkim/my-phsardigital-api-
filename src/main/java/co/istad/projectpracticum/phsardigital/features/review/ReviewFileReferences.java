package co.istad.projectpracticum.phsardigital.features.review;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A file used as the photo on a review. */
@Component
@RequiredArgsConstructor
public class ReviewFileReferences implements FileReferenceCheck {

    private final ReviewRepository reviewRepository;

    @Override
    public String referenceName() {
        return "a review photo";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return reviewRepository.existsByPhoto_ObjectName(objectName);
    }
}
