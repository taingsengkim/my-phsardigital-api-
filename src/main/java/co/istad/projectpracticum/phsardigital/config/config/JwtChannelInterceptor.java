package co.istad.projectpracticum.phsardigital.config.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("WS CONNECT rejected: no bearer token");
                throw new IllegalArgumentException("Missing Authorization header");
            }
            try {
                Jwt jwt = jwtDecoder.decode(authHeader.substring(7));
                String userId = jwt.getSubject();
                accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
                log.info("WS authenticated: {}", userId);
            } catch (Exception e) {
                log.error("WS CONNECT rejected: invalid token", e);
                throw new IllegalArgumentException("Invalid token");
            }
        }
        return message;
    }
}
