package co.istad.projectpracticum.phsardigital.config.config;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaEntityAuditorAware implements AuditorAware<String> {   @NullMarked
@Override
public Optional<String> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
        return Optional.of(jwtAuth.getToken().getSubject());
    }
    return Optional.of("SYSTEM");
}
}
