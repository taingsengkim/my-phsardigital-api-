package co.istad.projectpracticum.phsardigital.features.auth;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.core.ratelimit.RateLimiter;
import co.istad.projectpracticum.phsardigital.features.auth.dto.AccountEmailRequest;
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
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService{
    private static final String VERIFY_EMAIL_ACTION = "VERIFY_EMAIL";
    private static final String UPDATE_PASSWORD_ACTION = "UPDATE_PASSWORD";

    private final Keycloak keycloak;
    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminProps props;
    private final AuthMapper authMapper;
    private final UserProvisioningService userProvisioningService;
    private final UserProfileMapper userProfileMapper;
    private final SellerRepository sellerRepository;
    private final RateLimiter authEmailRateLimiter;

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

        UsersResource usersResource = keycloakUsers();
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

    @Override
    public void resendVerificationEmail(AccountEmailRequest request) {
        String email = normalizeEmail(request.email());
        try {
            UserRepresentation user = findByEmail(email);
            if (user == null) {
                return;
            }
            if (Boolean.TRUE.equals(user.isEmailVerified())) {
                // Sending anyway would let anyone who knows the address post a
                // "verify your account" mail to it whenever they liked.
                log.debug("Verification email not re-sent for {}: already verified", user.getId());
                return;
            }
            if (!withinEmailBudget(VERIFY_EMAIL_ACTION, email)) {
                return;
            }
            keycloakUsers().get(user.getId()).sendVerifyEmail();
            log.info("Re-sent the verification email for user {}", user.getId());
        } catch (Exception failure) {
            // The reply is fixed, so a failure here has nowhere to go but the log.
            log.error("Could not re-send a verification email", failure);
        }
    }

    @Override
    public void requestPasswordReset(AccountEmailRequest request) {
        String email = normalizeEmail(request.email());
        try {
            UserRepresentation user = findByEmail(email);
            if (user == null) {
                return;
            }
            if (!Boolean.TRUE.equals(user.isEnabled())) {
                // A suspended account gets no route back in; lifting the suspension is
                // an admin's decision, not something a reset link should work around.
                log.info("Password reset not sent for {}: the account is disabled", user.getId());
                return;
            }
            if (!withinEmailBudget(UPDATE_PASSWORD_ACTION, email)) {
                return;
            }
            keycloakUsers().get(user.getId()).executeActionsEmail(List.of(UPDATE_PASSWORD_ACTION));
            log.info("Sent a password reset email for user {} (provider: {})",
                    user.getId(), user.getFederationLink());
        } catch (Exception failure) {
            log.error("Could not send a password reset email", failure);
        }
    }

    /**
     * Whether this address has any of its mail allowance left for this action.
     *
     * <p>Checked immediately before sending rather than on the way in, which matters
     * more than it looks. Spending a token per <em>request</em> would let anyone drain
     * a chosen victim's allowance with three cheap calls naming an address they never
     * receive mail for, locking the real owner out of the only route back into their
     * account. Spending it per <em>mail</em> means the only way to exhaust somebody's
     * budget is to actually send them the mail, which is the thing this limit exists
     * to cap. It gives up nothing in return: an address-keyed limit cannot slow down
     * enumeration anyway, because a caller walking a list of addresses gets a fresh
     * bucket for every one of them. Stopping that is the per-caller limit's job.
     *
     * <p>Over budget is a silent drop, never an error. A refusal would only ever come
     * back for an address that had already triggered a mail — an account, in other
     * words — and telling a stranger that much is the one thing these endpoints are
     * built not to do.
     */
    private boolean withinEmailBudget(String action, String email) {
        if (authEmailRateLimiter.tryAcquire(action + ":" + email)) {
            return true;
        }
        log.info("Dropped a {} email: the address is over its allowance", action);
        return false;
    }

    /**
     * @return the account holding this address, or null when no account does.
     * Exact rather than a prefix search, so {@code sok@example.com} cannot be used to
     * fish for {@code sokha@example.com}.
     */
    private UserRepresentation findByEmail(String email) {
        List<UserRepresentation> matches = keycloakUsers().searchByEmail(email, true);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    /**
     * Keycloak stores addresses lowercased, so a search for {@code Sokha@Example.com}
     * finds nothing while the same account is reachable as {@code sokha@example.com}.
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UsersResource keycloakUsers() {
        return keycloak.realm(props.getTargetRealm()).users();
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
