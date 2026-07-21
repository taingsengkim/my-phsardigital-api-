package co.istad.projectpracticum.phsardigital.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) {
        //Security Mechani
        http.oauth2ResourceServer(oauth->
                oauth.jwt(Customizer.withDefaults()));
        http.cors(cors -> cors.configurationSource(request -> {
            var config = new org.springframework.web.cors.CorsConfiguration();
            config.setAllowedOriginPatterns(java.util.List.of("http://localhost:3000"));
            config.setAllowedMethods(java.util.List.of("*"));
            config.setAllowedHeaders(java.util.List.of("*"));
            return config;
        }));
        http.authorizeHttpRequests(auth ->
                auth.requestMatchers(HttpMethod.GET,"/api/v1/categories","/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/listings", "/api/v1/listings/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/categories/**").hasAnyRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/listings/**").hasAnyRole("SELLER","ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/listings/**").hasAnyRole("SELLER","ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/listings/**").hasAnyRole("SELLER","ADMIN")
                        .requestMatchers(HttpMethod.DELETE,"/api/v1/listings/**").hasAnyRole("SELLER","ADMIN")
                        .requestMatchers("/api/v1/carts/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/admin/seller-applications/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/user-profiles/**").authenticated()
                        .requestMatchers("/api/v1/seller-applications/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/purchases/seller/**").hasAnyRole("SELLER", "ADMIN")
                        .requestMatchers("/api/v1/purchases/**").hasAnyRole("USER", "SELLER", "ADMIN")
                        .requestMatchers("/api/v1/conversations/**").authenticated()
                        .requestMatchers("/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/scalar/**").permitAll()
                        .requestMatchers("/api/v1/files/**").permitAll()
                        .anyRequest().authenticated());

        http.sessionManagement(state ->
                state.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(){
        Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthroiteiesConverter = jwt -> {
            Map<String,Collection> realmAccess = jwt.getClaim("realm_access");
            Collection<String> roles = realmAccess.get("roles");
            return roles.stream()
                    .map(role->new SimpleGrantedAuthority("ROLE_"+role))
                    .collect(Collectors.toList());
        };
        var jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthroiteiesConverter);
        return jwtAuthenticationConverter;
    }

}
