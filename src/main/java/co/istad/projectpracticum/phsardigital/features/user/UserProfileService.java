package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.UpdateUserProfileRequest;
import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {

    UserProfileResponse getMe();

    UserProfileResponse updateMe(UpdateUserProfileRequest request);

    /**
     * Replaces the caller's profile picture. The previous image, if any, is
     * removed from object storage once the new one is attached.
     *
     * @param file the image to store
     * @return the refreshed profile, including the new avatar URL
     */
    UserProfileResponse uploadMyAvatar(MultipartFile file);

    /**
     * Removes the caller's profile picture and deletes it from object storage.
     *
     * @return the refreshed profile, with a null avatar
     */
    UserProfileResponse deleteMyAvatar();
}
