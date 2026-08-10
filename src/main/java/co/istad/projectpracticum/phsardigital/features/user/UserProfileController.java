package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.UpdateUserProfileRequest;
import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public UserProfileResponse getMe() {
        return userProfileService.getMe();
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateUserProfileRequest request) {
        return userProfileService.updateMe(request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadMyAvatar(@RequestPart("file") MultipartFile file) {
        return userProfileService.uploadMyAvatar(file);
    }

    @DeleteMapping("/me/avatar")
    public UserProfileResponse deleteMyAvatar() {
        return userProfileService.deleteMyAvatar();
    }
}
