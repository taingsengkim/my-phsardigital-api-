package co.istad.projectpracticum.phsardigital.config.security;

import co.istad.projectpracticum.phsardigital.core.exception.RestSecurityErrorHandler;
import co.istad.projectpracticum.phsardigital.core.ratelimit.RateLimiter;
import co.istad.projectpracticum.phsardigital.features.user.UserProvisioningService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {
    @Value("${app.cors.allowed-origin-patterns:http://localhost:3000}")
    private java.util.List<String> allowedOriginPatterns;

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http,
                                           RestSecurityErrorHandler securityErrorHandler,
                                           UserProvisioningService userProvisioningService,
                                           AuthRateLimitProps rateLimitProps,
                                           @Lazy @Qualifier("handlerExceptionResolver")
                                           HandlerExceptionResolver exceptionResolver) {
        //Security Mechani
        // Both the resource server and the chain itself are pointed at the same
        // handler, otherwise a rejected token returns an empty body while every
        // other failure returns the shared error shape.
        http.oauth2ResourceServer(oauth->
                oauth.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler));
        http.exceptionHandling(handling ->
                handling.authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler));
        http.cors(cors -> cors.configurationSource(request -> {
            var config = new org.springframework.web.cors.CorsConfiguration();
            config.setAllowedOriginPatterns(allowedOriginPatterns);
            config.setAllowedMethods(java.util.List.of("*"));
            config.setAllowedHeaders(java.util.List.of("*"));
            return config;
        }));
        http.authorizeHttpRequests(auth ->
                auth.requestMatchers(HttpMethod.GET,"/api/v1/categories","/api/v1/categories/**").permitAll()
                        // Before the open rule below, which would otherwise swallow it
                        // and hand the controller an anonymous request.
                        .requestMatchers(HttpMethod.GET,"/api/v1/listings/me").authenticated()
                        .requestMatchers(HttpMethod.GET,"/api/v1/listings", "/api/v1/listings/**").permitAll()
                        // A shop page is browsed before signing in, so its reviews are
                        // open. Ordered before that rule: /reviews/sellers/me answers
                        // for the current token and would otherwise be swallowed by
                        // the wildcard and reach the controller with no caller.
                        // A single * rather than **, so anything nested under a shop
                        // later has to be opened up deliberately.
                        .requestMatchers(HttpMethod.GET,"/api/v1/reviews/sellers/me").authenticated()
                        // The star breakdowns summarise reviews that are already public,
                        // and both sit a segment deeper than the wildcards below, which
                        // match one segment only — without their own rules they would
                        // fall through to authenticated() and a logged-out visitor would
                        // see a shop's reviews but not its rating.
                        .requestMatchers(HttpMethod.GET,"/api/v1/reviews/sellers/*/summary").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/reviews/listings/*/summary").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/reviews/sellers/*").permitAll()
                        // Read only, matching the shop's reviews above: posting, editing
                        // and deleting still fall through to authenticated().
                        .requestMatchers(HttpMethod.GET,"/api/v1/reviews/listings/*").permitAll()
                        // Shop pages had no rule at all and so fell through to
                        // authenticated() — a storefront a logged-out visitor could not
                        // open, while its reviews were public. /sellers/me is the one
                        // that answers for the current token, so it goes first.
                        // Before the /sellers/me rule below and well before the open
                        // shop pages: this one reports a shop's takings, so it needs the
                        // seller role rather than merely a token. Without an explicit
                        // rule it would fall through to authenticated() and answer any
                        // signed-in buyer.
                        .requestMatchers(HttpMethod.GET,"/api/v1/sellers/me/dashboard")
                        .hasAnyRole("SELLER","ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/sellers/me").authenticated()
                        .requestMatchers(HttpMethod.GET,"/api/v1/sellers/top").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/sellers/*","/api/v1/sellers/*/listings").permitAll()
                        // Ordered before the open /auth/** rule below: /auth/me answers
                        // for the current token, so it is the one auth endpoint that
                        // needs one. Registration stays anonymous.
                        .requestMatchers(HttpMethod.GET,"/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/categories","/api/v1/categories/**").hasAnyRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,"/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/listings/**").hasAnyRole("SELLER","ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/listings/**").hasAnyRole("SELLER","ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/listings/**").hasAnyRole("SELLER","ADMIN")
                        .requestMatchers(HttpMethod.DELETE,"/api/v1/listings/**").hasAnyRole("SELLER","ADMIN")
                        // This compatibility route mutates a seller-owned listing and
                        // its service deliberately enforces ownership. Admin moderation
                        // has separate endpoints; it does not edit a seller's catalogue.
                        .requestMatchers("/api/v1/listing_attributes/**").hasRole("SELLER")
                        .requestMatchers("/api/v1/carts/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/seller-applications/**").hasRole("ADMIN")
                        // Catch-all, so an admin endpoint added later is closed by
                        // default instead of falling through to authenticated().
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/user-profiles/**").authenticated()
                        .requestMatchers("/api/v1/addresses/**").authenticated()
                        .requestMatchers("/api/v1/seller-applications/**").hasAnyRole("USER", "ADMIN")
                        // The pricing page is read before subscribing, so it cannot
                        // require a subscriber. Everything else here is per-seller.
                        .requestMatchers(HttpMethod.GET, "/api/v1/subscriptions/plans").permitAll()
                        .requestMatchers("/api/v1/subscriptions/**").hasAnyRole("SELLER", "ADMIN")
                        // Subscriptions are the only thing paid for by KHQR today, and
                        // only a seller buys one. Ownership is enforced per payment as
                        // well: PaymentServiceImpl answers 404 for somebody else's.
                        .requestMatchers("/api/v1/payments/**").hasAnyRole("SELLER", "ADMIN")
                        // The counter is the seller's own till: only they ring up on it.
                        .requestMatchers("/api/v1/pos/**").hasAnyRole("SELLER", "ADMIN")
                        .requestMatchers("/api/v1/purchases/seller/**").hasAnyRole("SELLER", "ADMIN")
                        .requestMatchers("/api/v1/purchases/**").hasAnyRole("USER", "SELLER", "ADMIN")
                        .requestMatchers("/api/v1/conversations/**").authenticated()
                        .requestMatchers("/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/scalar/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Previews stay open so <img> tags work; writing and deleting do not.
                        // The preview endpoint refuses to describe private files, so
                        // opening it up does not expose seller documents.
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/**").authenticated()
                        // A role is the wrong gate for deletes: it let any seller remove
                        // another seller's images, while stopping a buyer removing their
                        // own avatar. FileUploadService checks the uploader instead.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/files/**").authenticated()
                        .anyRequest().authenticated());

        // After authentication, so the token is already on the context and the filter
        // has something to reconcile; before the controllers, so any endpoint can
        // assume the caller's row exists.
        http.addFilterAfter(new UserProvisioningFilter(userProvisioningService),
                BearerTokenAuthenticationFilter.class);

        // Ahead of token authentication, so a caller flooding the anonymous auth
        // endpoints is turned away before anything is parsed or looked up. Skipped
        // entirely when disabled, rather than installed with an infinite limit, so
        // there is no doubt about what the flag does.
        if (rateLimitProps.isEnabled()) {
            http.addFilterBefore(
                    new AuthRateLimitFilter(
                            new RateLimiter(rateLimitProps.getRequests(),
                                    rateLimitProps.getPeriod(),
                                    rateLimitProps.getMaxTrackedKeys()),
                            exceptionResolver),
                    BearerTokenAuthenticationFilter.class);
        }

        http.sessionManagement(state ->
                state.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * The second of the two limits, keyed by email address rather than by caller.
     *
     * <p>A bean because {@code AuthServiceImpl} is the only place that knows which
     * address a request names — the filter would have to read and re-buffer the
     * request body to find out. It is the sole {@link RateLimiter} bean; the
     * per-address limiter the filter uses is constructed inline above, since nothing
     * else needs a handle on it and two beans of one type would only invite an
     * ambiguous injection later.
     */
    @Bean
    public RateLimiter authEmailRateLimiter(AuthRateLimitProps rateLimitProps) {
        return new RateLimiter(
                rateLimitProps.getEmailsPerAddress(),
                rateLimitProps.getEmailPeriod(),
                rateLimitProps.getMaxTrackedKeys());
    }

    /**
     * Maps Keycloak's realm roles onto Spring authorities.
     *
     * <p>Both lookups are guarded because a token really can arrive without them.
     * Keycloak omits {@code realm_access} entirely for a user who holds no realm
     * role, and that is exactly what a brand-new social sign-in looks like when the
     * realm has no default role configured — so the unguarded version answered every
     * such request with a 500 from inside the security filter, which is close to
     * undiagnosable. An empty authority list is the honest answer: they are
     * authenticated, they are simply not authorised for anything yet.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(){
        Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthroiteiesConverter = jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null) {
                return List.of();
            }
            if (!(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(String::valueOf)
                    .map(role->new SimpleGrantedAuthority("ROLE_"+role))
                    .collect(Collectors.toList());
        };
        var jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthroiteiesConverter);
        return jwtAuthenticationConverter;
    }

}
