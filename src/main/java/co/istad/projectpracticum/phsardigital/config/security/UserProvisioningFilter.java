package co.istad.projectpracticum.phsardigital.config.security;

import co.istad.projectpracticum.phsardigital.features.user.UserProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reconciles the caller's database row with their access token on every
 * authenticated request.
 *
 * <p>The alternative was to provision only when somebody opens their profile page,
 * which is where this logic used to live. That leaves a hole: a user who signs in
 * with Google and goes straight to browsing exists in Keycloak and nowhere else, so
 * the first thing they try that references a user — a cart, a message, a
 * favourite — fails on a row that was never written. Doing it here means "logged in"
 * and "known to us" cannot drift apart, whichever endpoint they happen to hit first.
 *
 * <p>Cost is one primary-key lookup per request, and a write only when a claim
 * actually differs from what is stored.
 *
 * <p>Deliberately not a {@code @Component}. Boot auto-registers any {@code Filter}
 * bean into the servlet chain, which runs ahead of Spring Security — so the same
 * instance would fire once with an empty security context, set the
 * already-filtered marker {@link OncePerRequestFilter} uses, and make the copy
 * inside the security chain skip itself. {@code SecurityConfig} constructs this
 * directly instead.
 */
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningFilter extends OncePerRequestFilter {

    private final UserProvisioningService userProvisioningService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth && auth.isAuthenticated()) {
            try {
                userProvisioningService.syncFromToken(jwtAuth.getToken());
            } catch (Exception failure) {
                // Deliberately swallowed. A token this filter cannot turn into a row —
                // a provider that withheld the email, an address already taken by
                // another account — is a real problem, but failing every request the
                // user makes is a worse way to report it than letting the endpoint they
                // called answer for itself. GET /api/v1/auth/me runs the same
                // reconciliation without this guard and returns the actual error, which
                // is why the frontend should call it right after sign-in.
                log.warn("Could not reconcile the profile for {} from its token",
                        jwtAuth.getToken().getSubject(), failure);
            }
        }

        filterChain.doFilter(request, response);
    }
}
