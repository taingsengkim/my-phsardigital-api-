package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.features.user.dto.UpdateUserProfileRequest;
import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminProps props;
    private final Keycloak keycloak;
    private final UserProfileMapper profileMapper;
    @Override
    public UserProfileResponse getMe() {
        String userId = AuthUtils.extractUserId();
        return profileMapper.toResponse(userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found.")));
    }

    @Override
    @Transactional
    public UserProfileResponse updateMe(UpdateUserProfileRequest request) {
        String userId = AuthUtils.extractUserId();
        UserResource userResource = keycloak.realm(props.getTargetRealm())
                .users().get(userId);
        UserRepresentation keycloakUser = userResource.toRepresentation();

        if (request.firstName() != null) {
            keycloakUser.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            keycloakUser.setLastName(request.lastName());
        }
        userResource.update(keycloakUser);
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found."));
        if (request.firstName() != null || request.lastName() != null) {
            profile.setFullName(
                    (keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : "") + " " +
                            (keycloakUser.getLastName() != null ? keycloakUser.getLastName() : ""));
        }
        if (request.phone() != null)     profile.setPhone(request.phone());
        if (request.avatarUrl() != null) profile.setAvatarUrl(request.avatarUrl());
        if (request.dateOfBirth() != null) profile.setDateOfBirth(request.dateOfBirth());
        if (request.phone() != null) profile.setPhone(request.phone());

        userProfileRepository.save(profile);

        return profileMapper.toResponse(profile);
    }


}