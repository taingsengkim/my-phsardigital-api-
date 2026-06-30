package co.istad.projectpracticum.phsardigital.features.auth;

import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterRequest;

public interface AuthService {

    /**
     * Registers a new user account.
     *
     * @param registerRequest the registration request containing the user's
     *                        username, password, email, and other required information
     */
    void register(RegisterRequest registerRequest);
}
