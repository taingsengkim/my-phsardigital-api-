package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A file used as somebody's avatar. */
@Component
@RequiredArgsConstructor
public class UserFileReferences implements FileReferenceCheck {

    private final UserProfileRepository userProfileRepository;

    @Override
    public String referenceName() {
        return "a profile avatar";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return !userProfileRepository.findAllByAvatarFile_ObjectName(objectName).isEmpty();
    }
}
