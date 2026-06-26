package co.istad.sengkim.phsardigital.config.config;

import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaEntityAuditorAware implements AuditorAware<String> {
    @NullMarked
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext()
                .getAuthentication();
        if(auth!=null){
            Jwt jwt =(Jwt) auth.getPrincipal();
            if(jwt != null){
                String username =jwt.getClaimAsString("preferred_username");
                return Optional.of(username);
            }
        }
        return Optional.of("SYSTEM");
    }
}
