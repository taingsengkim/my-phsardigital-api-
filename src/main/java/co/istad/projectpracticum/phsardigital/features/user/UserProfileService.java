package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.UpdateUserProfileRequest;
import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;

public interface UserProfileService {
    UserProfileResponse getMe();
    UserProfileResponse updateMe(UpdateUserProfileRequest request);
}