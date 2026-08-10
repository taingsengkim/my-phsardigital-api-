package co.istad.projectpracticum.phsardigital.config.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Names the published API and declares the bearer scheme, which is what puts
     * the Authorize button in Swagger UI. Registering it as a top-level security
     * requirement makes every operation send the token, so a single Authorize
     * covers the whole document instead of one annotation per controller.
     */
    @Bean
    public OpenAPI phsardigitalOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("phsardigital")
                        .description("PhsarDigital marketplace API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the Keycloak access token only — "
                                        + "Swagger adds the \"Bearer \" prefix itself.")));
    }
}
