package co.istad.projectpracticum.phsardigital.features.auth;

import co.istad.projectpracticum.phsardigital.features.auth.dto.AccountEmailRequest;
import co.istad.projectpracticum.phsardigital.features.auth.dto.AccountEmailResponse;
import co.istad.projectpracticum.phsardigital.features.auth.dto.MeResponse;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterRequest;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest registerRequest){
        return authService.register(registerRequest);
    }

    /**
     * Sends the verification email again, for the user who never received the one
     * registration sent.
     *
     * <p>202 rather than 200, and always 202: the work happens whether or not there
     * is an account to do it for, and the reply says only that the request was
     * accepted. A 404 for an unknown address would turn this into a way to check
     * whether somebody has an account here.
     */
    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountEmailResponse resendVerificationEmail(@Valid @RequestBody AccountEmailRequest request) {
        authService.resendVerificationEmail(request);
        return AccountEmailResponse.accepted();
    }

    /**
     * Emails a link for setting a new password. Answers identically to
     * {@link #resendVerificationEmail}, for the same reason.
     */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountEmailResponse requestPasswordReset(@Valid @RequestBody AccountEmailRequest request) {
        authService.requestPasswordReset(request);
        return AccountEmailResponse.accepted();
    }

    /**
     * Who the bearer token belongs to, recording the account here if this is the
     * first time we have seen it. Call this once straight after sign-in — for a
     * social sign-in it is what turns a Keycloak account into a row in our database.
     */
    @GetMapping("/me")
    public MeResponse me(){
        return authService.me();
    }
}
