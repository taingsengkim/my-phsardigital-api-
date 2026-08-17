package co.istad.projectpracticum.phsardigital.features.auth;

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
