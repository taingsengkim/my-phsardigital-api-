package co.istad.projectpracticum.phsardigital.config.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Limits for the anonymous endpoints under {@code /api/v1/auth}.
 *
 * <p>Two limits rather than one, because they stop different things. The per-address
 * limit stops one host registering accounts in bulk or walking the endpoint to find
 * out which emails exist. The per-email limit stops a distributed caller using the
 * verification and password-reset endpoints to bury one particular person's inbox —
 * spread across enough source addresses, the first limit never notices that.
 */
@Configuration
@ConfigurationProperties(prefix = "app.auth.rate-limit")
@Setter
@Getter
@NoArgsConstructor
public class AuthRateLimitProps {

    private boolean enabled = true;

    /**
     * Requests one client address may make back to back across every POST under
     * {@code /api/v1/auth}.
     */
    private int requests = 5;

    private Duration period = Duration.ofMinutes(1);

    /**
     * Mails one email address may trigger, counted separately for verification and
     * for password reset.
     */
    private int emailsPerAddress = 3;

    private Duration emailPeriod = Duration.ofHours(1);

    /**
     * How many distinct keys either limiter tracks before sweeping idle ones.
     */
    private int maxTrackedKeys = 50_000;
}
