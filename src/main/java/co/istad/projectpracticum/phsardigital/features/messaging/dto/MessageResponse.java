package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageResponse(
        UUID uuid,
        UUID conversationUuid,
        String senderId,
        String senderName,
        String body,
        Boolean isRead,
        LocalDateTime sentAt
) {
}