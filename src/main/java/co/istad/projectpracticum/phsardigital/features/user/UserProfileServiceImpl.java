package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.features.user.dto.UpdateUserProfileRequest;
import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminProps props;
    private final Keycloak keycloak;
    private final UserProfileMapper profileMapper;
    @Override
    public UserProfileResponse getMe() {
        String userId = AuthUtils.extractUserId();
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found."));
        return profileMapper.toResponse(userProfile);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMe(UpdateUserProfileRequest request) {
        String userId = AuthUtils.extractUserId();
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found."));

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
                profile.setFullName(fullName(keycloakUser));
            }
            if (request.phone() != null) {
                profile.setPhone(request.phone());
            }
            if (request.avatarUrl() != null) {
                profile.setAvatarUrl(request.avatarUrl().trim());
            }
            if (request.dateOfBirth() != null) {
                profile.setDateOfBirth(request.dateOfBirth());
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

    private String fullName(UserRepresentation user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName();
        String lastName = user.getLastName() == null ? "" : user.getLastName();
        return (firstName + " " + lastName).trim();
    }
}
