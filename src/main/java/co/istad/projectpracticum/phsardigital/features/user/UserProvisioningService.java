package co.istad.projectpracticum.phsardigital.features.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns an access token into a row in {@code users}.
 *
 * <p>This is the only place an identity arrives in our database from outside, and
 * it exists because registration is not the only way in. A social sign-in never
 * touches {@code POST /api/v1/auth/register}: the browser goes to Keycloak,
 * Keycloak talks to Google, and the first thing this API ever sees is a bearer
 * token for a user it has no record of. The same is true of accounts made in the
 * Keycloak admin console and of realm imports.
 *
 * <p>So the token itself is treated as the source of truth for identity, and every
 * authenticated request reconciles against it. Keycloak owns email, name and
 * verification state; we own everything it does not know about — phone, gender,
 * bio, avatar — and those are never touched here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningService {

    private final UserProfileRepository userProfileRepository;

    /**
     * Creates the caller's profile if it is missing and refreshes the fields the
     * token owns.
     *
     * @param token the caller's decoded access token
     * @return the stored profile
     * @throws ResponseStatusException 409 when the token's email already belongs to a
     *         different account, 422 when a new account's token carries no email
     */
    @Transactional
    public UserProfile syncFromToken(Jwt token) {
        String userId = token.getSubject();

        UserProfile existing = userProfileRepository.findById(userId).orElse(null);
        UserProfile profile = existing != null ? existing : provisionFromToken(token);

        boolean changed = applyTokenClaims(profile, token);
        if (existing == null || changed) {
            return save(profile, existing == null);
        }
        return profile;
    }

    private UserProfile save(UserProfile profile, boolean isNew) {
        try {
            return userProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException collision) {
            if (!isNew) {
                throw collision;
            }
            // Almost always one thing: Keycloak made a second account for someone who
            // already signed up with a password, because the identity provider is not
            // set to link by email. Two Keycloak subjects, one address, and our unique
            // constraint is the first thing that notices. A 500 here would send them
            // hunting through Hibernate; name the cause instead.
            log.warn("Could not provision {} from its token; email or username already belongs "
                            + "to another account. Check the identity provider's first-login "
                            + "flow links existing accounts by email.",
                    profile.getId(), collision);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists. Sign in with the method you "
                            + "used originally, then link your social account from your profile.");
        }
    }

    private UserProfile provisionFromToken(Jwt token) {
        String email = token.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            // Facebook can withhold the address when the user hides it, and Keycloak
            // passes on what it was given. Nothing downstream works without one.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Your sign-in provider did not share an email address, which this "
                            + "account needs. Allow email sharing and try again.");
        }
        UserProfile profile = new UserProfile(token.getSubject());
        profile.setEmail(email);
        profile.setStatus(UserStatus.ACTIVE);
        log.info("Provisioned a user profile for {} from its access token (provider: {})",
                token.getSubject(), token.getClaimAsString("identity_provider"));
        return profile;
    }

    /**
     * Copies identity data the token already carries onto the profile.
     *
     * @return true when at least one field changed and the row needs saving
     */
    private boolean applyTokenClaims(UserProfile profile, Jwt token) {
        boolean changed = false;
        boolean nameChanged = false;

        String email = token.getClaimAsString("email");
        if (email != null && !email.equals(profile.getEmail())) {
            profile.setEmail(email);
            changed = true;
        }

        String username = token.getClaimAsString("preferred_username");
        if (username != null && !username.equals(profile.getUsername())) {
            profile.setUsername(username);
            changed = true;
        }

        String firstName = token.getClaimAsString("given_name");
        if (firstName != null && !firstName.equals(profile.getFirstName())) {
            profile.setFirstName(firstName);
            nameChanged = true;
        }

        String lastName = token.getClaimAsString("family_name");
        if (lastName != null && !lastName.equals(profile.getLastName())) {
            profile.setLastName(lastName);
            nameChanged = true;
        }

        Boolean emailVerified = token.getClaim("email_verified");
        if (emailVerified != null && !emailVerified.equals(profile.getEmailVerified())) {
            profile.setEmailVerified(emailVerified);
            changed = true;
        }

        String picture = token.getClaimAsString("picture");
        if (picture != null && !picture.equals(profile.getExternalAvatarUrl())) {
            profile.setExternalAvatarUrl(picture);
            changed = true;
        }

        String provider = token.getClaimAsString("identity_provider");
        if (provider != null && !provider.equals(profile.getIdentityProvider())) {
            profile.setIdentityProvider(provider);
            changed = true;
        }

        // Some providers send only a single "name" claim. Falling back to it keeps
        // the display name from being blank for those accounts, but it must not
        // overwrite the parts when they were supplied.
        if (nameChanged) {
            profile.refreshFullName();
        } else if (profile.getFullName() == null) {
            String name = token.getClaimAsString("name");
            if (name != null && !name.isBlank()) {
                profile.setFullName(name.trim());
                changed = true;
            }
        }

        return changed || nameChanged;
    }
}
