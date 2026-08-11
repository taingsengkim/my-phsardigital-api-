package co.istad.projectpracticum.phsardigital.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Rejections raised by the security filter chain never reach a controller, so
 * {@link AppGlobalException} never sees them and the caller gets an empty 401
 * or 403 body. This hands those two cases back to the MVC exception machinery
 * so they render the same {@link RestErrorResponse} as everything else.
 * <p>
 * The Bearer-token defaults still run first: they set the
 * {@code WWW-Authenticate} header, which is part of the OAuth2 contract and
 * carries its own error description.
 */
@Component
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

    private final BearerTokenAccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();

    private final HandlerExceptionResolver exceptionResolver;

    /**
     * The resolver is injected lazily because the security filter chain is built
     * before the MVC infrastructure it belongs to.
     */
    public RestSecurityErrorHandler(
            @Lazy @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver
    ) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) {
        bearerEntryPoint.commence(request, response, exception);
        exceptionResolver.resolveException(request, response, null, exception);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) {
        bearerAccessDeniedHandler.handle(request, response, exception);
        exceptionResolver.resolveException(request, response, null, exception);
    }
}
