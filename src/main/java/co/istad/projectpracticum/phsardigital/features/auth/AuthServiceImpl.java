package co.istad.projectpracticum.phsardigital.features.auth;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.features.auth.dto.MeResponse;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterRequest;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileMapper;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import co.istad.projectpracticum.phsardigital.features.user.UserProvisioningService;
import co.istad.projectpracticum.phsardigital.features.user.UserStatus;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService{
    private static final String VERIFY_EMAIL_ACTION = "VERIFY_EMAIL";

    private final Keycloak keycloak;
    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminProps props;
    private final AuthMapper authMapper;
    private final UserProvisioningService userProvisioningService;
    private final UserProfileMapper userProfileMapper;
    private final SellerRepository sellerRepository;

    @Override
    @Transactional
    public MeResponse me() {
        Jwt token = AuthUtils.extractToken();
        UserProfile profile = userProvisioningService.syncFromToken(token);

        // The seller id is the Keycloak subject, so the profile's existence is the
        // whole answer — no second identifier to look up.
        boolean isSeller = sellerRepository.existsById(profile.getId());

        return MeResponse.builder()
                .userId(profile.getId())
                .username(profile.getUsername())
                .email(profile.getEmail())
                .fullName(profile.getFullName())
                .phone(profile.getPhone())
                .avatarUrl(userProfileMapper.avatarUrl(profile))
                .roles(realmRoles(token))
                .isSeller(isSeller)
                .sellerId(isSeller ? profile.getId() : null)
                .build();
    }

    /**
     * Reads the realm roles straight off the token rather than asking Keycloak, so
     * signing in costs no round trip to the identity server.
     */
    @SuppressWarnings("unchecked")
    private List<String> realmRoles(Jwt token) {
        Map<String, Object> realmAccess = token.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        return roles instanceof Collection<?> collection
                ? List.copyOf((Collection<String>) collection)
                : List.of();
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }

        UsersResource usersResource = keycloak.realm(props.getTargetRealm()).users();
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(request.username().trim());
        userRepresentation.setEmail(request.email().trim());
        userRepresentation.setFirstName(request.firstName().trim());
        userRepresentation.setLastName(request.lastName().trim());
        userRepresentation.setEnabled(true);
        userRepresentation.setEmailVerified(false);
        userRepresentation.setRequiredActions(List.of(VERIFY_EMAIL_ACTION));

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);
        userRepresentation.setCredentials(List.of(credential));

        String createdUserId = createKeycloakUser(usersResource, userRepresentation);
        UserResource createdUserResource = usersResource.get(createdUserId);

        try {
            RoleRepresentation userRole = keycloak.realm(props.getTargetRealm())
                    .roles().get(RoleEnum.USER.name()).toRepresentation();
            createdUserResource.roles().realmLevel().add(List.of(userRole));

            UserProfile userProfile = new UserProfile();
            userProfile.setId(createdUserId);
            userProfile.setEmail(userRepresentation.getEmail());
            userProfile.setUsername(userRepresentation.getUsername());
            userProfile.setPhone(request.phoneNumber());
            userProfile.setFirstName(userRepresentation.getFirstName());
            userProfile.setLastName(userRepresentation.getLastName());
            userProfile.refreshFullName();
            userProfile.setEmailVerified(false);
            userProfile.setStatus(UserStatus.ACTIVE);
            userProfileRepository.saveAndFlush(userProfile);
        } catch (Exception registrationFailure) {
            removeKeycloakUser(createdUserResource, createdUserId);
            log.error("Registration failed after Keycloak user creation; compensation attempted for user {}",
                    createdUserId, registrationFailure);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Registration could not be completed. Please try again."
            );
        }

        try {
            createdUserResource.sendVerifyEmail();
        } catch (Exception emailFailure) {
            // VERIFY_EMAIL remains a required action, so a temporary email outage must
            // not create a false registration failure or delete a valid account.
            log.warn("User {} was registered, but the verification email could not be sent",
                    createdUserId, emailFailure);
        }

        userRepresentation.setId(createdUserId);
        return authMapper.toRegisterResponse(request, userRepresentation);
    }

    private String createKeycloakUser(UsersResource usersResource, UserRepresentation userRepresentation) {
        try (Response response = usersResource.create(userRepresentation)) {
            int status = response.getStatus();
            if (status == HttpStatus.CREATED.value()) {
                return CreatedResponseUtil.getCreatedId(response);
            }
            if (status == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Username or email already exists."
                );
            }
            if (status >= 400 && status < 500) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Registration was rejected. Check the account details and password policy."
                );
            }
            log.error("Keycloak returned status {} while creating a user", status);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Identity service is temporarily unavailable."
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Keycloak user creation failed", exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Identity service is temporarily unavailable."
            );
        }
    }

    private void removeKeycloakUser(UserResource userResource, String userId) {
        try {
            userResource.remove();
        } catch (Exception cleanupFailure) {
            log.error("Could not remove partially registered Keycloak user {}", userId, cleanupFailure);
        }
    }
}
