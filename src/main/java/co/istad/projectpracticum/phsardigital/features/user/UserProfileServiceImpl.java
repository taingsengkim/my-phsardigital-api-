package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.user.dto.UpdateUserProfileRequest;
import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {

    private static final String AVATAR_FOLDER = "avatars";

    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminProps props;
    private final Keycloak keycloak;
    private final UserProfileMapper profileMapper;
    private final FileUploadService fileUploadService;
    private final UserProvisioningService userProvisioningService;

    @Override
    @Transactional
    public UserProfileResponse getMe() {
        return profileMapper.toResponse(currentProfile());
    }

    @Override
    @Transactional
    public UserProfileResponse updateMe(UpdateUserProfileRequest request) {
        String userId = AuthUtils.extractUserId();
        UserProfile profile = currentProfile();

        UserResource userResource = keycloak.realm(props.getTargetRealm())
                .users().get(userId);
        UserRepresentation keycloakUser;
        try {
            keycloakUser = userResource.toRepresentation();
        } catch (Exception exception) {
            log.error("Could not load Keycloak identity for profile {}", userId, exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Identity service is temporarily unavailable."
            );
        }

        String previousFirstName = keycloakUser.getFirstName();
        String previousLastName = keycloakUser.getLastName();
        boolean identityChanged = false;

        if (request.firstName() != null) {
            keycloakUser.setFirstName(request.firstName().trim());
            identityChanged = true;
        }
        if (request.lastName() != null) {
            keycloakUser.setLastName(request.lastName().trim());
            identityChanged = true;
        }

        if (identityChanged) {
            try {
                userResource.update(keycloakUser);
            } catch (Exception exception) {
                log.error("Could not update Keycloak identity for profile {}", userId, exception);
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Identity service is temporarily unavailable."
                );
            }
        }

        try {
            if (identityChanged) {
                profile.setFirstName(keycloakUser.getFirstName());
                profile.setLastName(keycloakUser.getLastName());
                profile.refreshFullName();
            }
            if (request.phone() != null) {
                profile.setPhone(request.phone());
            }
            if (request.dateOfBirth() != null) {
                profile.setDateOfBirth(request.dateOfBirth());
            }
            if (request.gender() != null) {
                profile.setGender(request.gender());
            }
            if (request.bio() != null) {
                profile.setBio(request.bio().trim());
            }

            UserProfile saved = userProfileRepository.saveAndFlush(profile);
            return profileMapper.toResponse(saved);
        } catch (RuntimeException databaseFailure) {
            if (identityChanged) {
                restoreKeycloakName(userResource, keycloakUser, previousFirstName, previousLastName, userId);
            }
            throw databaseFailure;
        }
    }

    @Override
    @Transactional
    public UserProfileResponse uploadMyAvatar(MultipartFile file) {
        UserProfile profile = currentProfile();
        FileUpload previousAvatar = profile.getAvatarFile();

        profile.setAvatarFile(fileUploadService.uploadImage(file, AVATAR_FOLDER));
        UserProfile saved = userProfileRepository.saveAndFlush(profile);

        // Flushed first, so the old row is no longer referenced when it is removed.
        if (previousAvatar != null) {
            fileUploadService.deleteQuietly(previousAvatar);
        }
        return profileMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserProfileResponse deleteMyAvatar() {
        UserProfile profile = currentProfile();
        FileUpload previousAvatar = profile.getAvatarFile();
        if (previousAvatar == null) {
            return profileMapper.toResponse(profile);
        }

        profile.setAvatarFile(null);
        UserProfile saved = userProfileRepository.saveAndFlush(profile);
        fileUploadService.deleteQuietly(previousAvatar);
        return profileMapper.toResponse(saved);
    }

    /**
     * Loads the caller's profile, creating it from the access token when it is
     * missing. Accounts created straight in Keycloak — imports, social login, the
     * admin console — never pass through registration, and used to be locked out
     * of every profile endpoint with a permanent 404.
     */
    private UserProfile currentProfile() {
        return userProvisioningService.syncFromToken(AuthUtils.extractToken());
    }

    private void restoreKeycloakName(
            UserResource userResource,
            UserRepresentation user,
            String firstName,
            String lastName,
            String userId
    ) {
        try {
            user.setFirstName(firstName);
            user.setLastName(lastName);
            userResource.update(user);
        } catch (Exception compensationFailure) {
            log.error("Could not restore Keycloak identity after profile update failure for {}",
                    userId, compensationFailure);
        }
    }
}
