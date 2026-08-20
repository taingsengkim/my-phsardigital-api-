package co.istad.projectpracticum.phsardigital.config.security;

import co.istad.projectpracticum.phsardigital.core.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Throttles anonymous writes to {@code /api/v1/auth}.
 *
 * <p>Everything under that prefix is {@code permitAll()}, and each POST there is
 * expensive in a way most endpoints are not: registration calls the Keycloak admin
 * API three times and sends a mail, and the verification and reset endpoints exist
 * purely to send mail. Unthrottled, that is an open relay pointed at whichever
 * address the caller names and a way to exhaust Keycloak's admin connections from a
 * laptop. GETs are left alone — {@code /auth/me} is authenticated and cheap.
 *
 * <p>Rejections are rendered through the MVC exception machinery rather than written
 * here, so a 429 comes back in the same {@code RestErrorResponse} shape as every
 * other failure instead of as a bare status with an empty body.
 *
 * <p>Deliberately not a {@code @Component}, for the reason spelled out on
 * {@link UserProvisioningFilter}: Boot would auto-register it into the servlet chain
 * as well, and the copy that ran first would mark the request already-filtered.
 */
@RequiredArgsConstructor
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String UNKNOWN_CLIENT = "unknown";

    private final RateLimiter rateLimiter;
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String client = clientKey(request);
        if (rateLimiter.tryAcquire(client)) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = rateLimiter.retryAfterSeconds(client);
        log.warn("Throttled {} {} from {}; {}s until the next attempt is allowed",
                request.getMethod(), request.getRequestURI(), client, retryAfter);

        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        exceptionResolver.resolveException(request, response, null,
                new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many attempts. Try again in %d seconds.".formatted(retryAfter)));
    }

    /**
     * The caller's address as the servlet container reports it. Behind a proxy that
     * is the proxy itself, which would throttle every user as one — hence
     * {@code server.forward-headers-strategy: framework}, which makes Spring rewrite
     * the remote address from {@code X-Forwarded-For} before this filter runs. Read
     * deliberately from the request rather than from the header directly: the header
     * is caller-supplied and trivially spoofed, so honouring it unconditionally would
     * hand an attacker a fresh bucket per request.
     */
    private String clientKey(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? UNKNOWN_CLIENT : address;
    }
}
