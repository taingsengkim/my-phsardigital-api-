package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A file used as a category icon. */
@Component
@RequiredArgsConstructor
public class CategoryFileReferences implements FileReferenceCheck {

    private final CategoryRepository categoryRepository;

    @Override
    public String referenceName() {
        return "a category icon";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return !categoryRepository.findAllByIconFile_ObjectName(objectName).isEmpty();
    }
}
