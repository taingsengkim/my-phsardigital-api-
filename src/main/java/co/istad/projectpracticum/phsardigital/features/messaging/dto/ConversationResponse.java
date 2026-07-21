package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationResponse(
        UUID uuid,
        String otherUserId,
        String otherUserName,
        String otherUserAvatar,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount
) {
}