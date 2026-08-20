package co.istad.projectpracticum.phsardigital.features.auth;

import co.istad.projectpracticum.phsardigital.features.auth.dto.AccountEmailRequest;
import co.istad.projectpracticum.phsardigital.features.auth.dto.MeResponse;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterRequest;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterResponse;

public interface AuthService {

    /**
     * Registers a new user account.
     *
     * @param registerRequest the registration request containing the user's
     *                        username, password, email, and other required information
     */
    RegisterResponse register(RegisterRequest registerRequest);

    /**
     * Sends the account verification email again.
     *
     * <p>Registration already sends one, but that mail can be filtered, mistyped past
     * or simply lost, and without this the account is unreachable: the address cannot
     * be registered a second time and there is no other way to ask for a new link.
     *
     * <p>Returns normally whatever happens — for an address with no account, for one
     * that is already verified, and for a mail server that is down. Nothing about the
     * account may leak out through this endpoint, so nothing about it is reported.
     */
    void resendVerificationEmail(AccountEmailRequest request);

    /**
     * Emails a password reset link.
     *
     * <p>Keycloak owns the reset itself: the mail carries a one-time link into its
     * {@code UPDATE_PASSWORD} flow, so no password ever passes through this API and
     * the realm's password policy applies to whatever the user chooses.
     *
     * <p>Silent in exactly the same way as {@link #resendVerificationEmail}, and for
     * the same reason.
     */
    void requestPasswordReset(AccountEmailRequest request);

    /**
     * Reconciles the caller's profile with their access token and answers who they
     * are, including their roles and whether they run a shop.
     *
     * <p>This is what a client calls immediately after any sign-in, and the only
     * call that has to happen for a social sign-in to be recorded — the browser goes
     * straight from Keycloak to Google and back, so this request is the first time
     * the API learns the account exists. Unlike the background reconciliation that
     * runs on every request, failures here are reported rather than logged, so a
     * provider that withheld an email surfaces at sign-in instead of as a confusing
     * error three screens later.
     */
    MeResponse me();
}
