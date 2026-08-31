package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param listing the product this message was sent about, or null for ordinary chat.
 *                Render it as a card above the bubble, so the shop can see what the
 *                question refers to without having to ask.
 */
public record MessageResponse(
        UUID uuid,
        UUID conversationUuid,
        String senderId,
        String senderName,
        String body,
        Boolean isRead,
        LocalDateTime sentAt,
        ListingContextResponse listing
) {
}