package co.istad.projectpracticum.phsardigital.config.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    /**
     * A single injectable application clock. The system zone preserves the wall-clock
     * semantics of existing {@code LocalDateTime} database columns, while callers can
     * still publish unambiguous {@code Instant} values at API boundaries.
     */
    @Bean
    public Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
