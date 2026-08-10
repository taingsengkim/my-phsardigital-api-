package co.istad.projectpracticum.phsardigital.config.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

public class AuthUtils {
    private AuthUtils(){}
    public static String extractUserId(){
        return extractToken().getSubject();
    }
    public static String extractJwt(){
        if(getAuth().getPrincipal()!=null){
            return ((Jwt) getAuth().getPrincipal()).getTokenValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"You have been unauthorized");
    }
    public static Authentication getAuth(){
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * @return the caller's decoded access token.
     * @throws ResponseStatusException when the caller is anonymous.
     */
    public static Jwt extractToken(){
        Authentication auth = getAuth();
        if (auth == null || auth instanceof AnonymousAuthenticationToken
                || !(auth instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You have been forbidden");
        }
        return jwtAuthenticationToken.getToken();
    }

    /**
     * Reads a string claim straight off the access token, avoiding a round trip
     * to the identity server for data the token already carries.
     *
     * @param name the claim name
     * @return the claim value, or null when absent
     */
    public static String extractClaim(String name){
        return extractToken().getClaimAsString(name);
    }

    /**
     * @param name the claim name
     * @return the boolean claim value, or false when absent
     */
    public static boolean extractBooleanClaim(String name){
        Boolean value = extractToken().getClaim(name);
        return Boolean.TRUE.equals(value);
    }
}
