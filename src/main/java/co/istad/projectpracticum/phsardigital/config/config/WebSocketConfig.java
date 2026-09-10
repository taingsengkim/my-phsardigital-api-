package co.istad.projectpracticum.phsardigital.config.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** How often each side must be heard from, in milliseconds: {write, read}. */
    private static final long[] HEARTBEAT = {10_000, 10_000};

    private final JwtChannelInterceptor jwtChannelInterceptor;

    private TaskScheduler heartbeatScheduler;

    /**
     * The scheduler that sends heartbeats, injected by name and lazily.
     *
     * <p>By name because {@code @Scheduled} elsewhere in the application means Boot
     * contributes a {@code TaskScheduler} of its own, and by type this would be
     * ambiguous.
     *
     * <p>Lazily because {@code messageBrokerTaskScheduler} is defined by the same
     * configuration that collects this class as a configurer, so asking for it eagerly —
     * in the constructor or in a plain setter — is a cycle Spring cannot resolve:
     * building the scheduler needs the configurer list, and building this configurer
     * needs the scheduler. The proxy defers that to first use, by which time both exist.
     */
    @Autowired
    public void setHeartbeatScheduler(
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Without heartbeats a half-open connection is invisible to both ends: a phone
        // that lost its network keeps a socket it believes is live, and the server holds
        // a session nobody is listening on. Messages are pushed into it and dropped, and
        // the only way out is for the user to reload. Ten seconds is short enough that a
        // client notices before a person does.
        //
        // A scheduler is not optional here — setHeartbeatValue fails at startup without
        // one, since something has to do the sending.
        registry.enableSimpleBroker("/queue", "/topic")
                .setHeartbeatValue(HEARTBEAT)
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");   // enables /user/queue/...
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}