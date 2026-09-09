package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import co.istad.projectpracticum.phsardigital.features.messaging.MessageType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param type     which renderer the bubble needs. Derived from whether a recording is
 *                 attached, so it can never contradict the other fields.
 * @param body     null on a voice note sent without a caption
 * @param voiceUrl a <em>presigned, expiring</em> link to the recording, or null on a text
 *                 message. It is signed because voice notes live in the private bucket,
 *                 which has a consequence for the client: the link goes stale, so play
 *                 from the URL the message arrived with rather than caching it for the
 *                 life of the session, and re-read the thread if playback starts failing.
 * @param voiceDurationSeconds as reported by the recorder that made it — a label, not a
 *                 measurement. See {@code SendMessageRequest}.
 * @param listing  the product this message was sent about, or null for ordinary chat.
 *                 Render it as a card above the bubble, so the shop can see what the
 *                 question refers to without having to ask.
 */
public record MessageResponse(
        UUID uuid,
        UUID conversationUuid,
        String senderId,
        String senderName,
        MessageType type,
        String body,
        String voiceUrl,
        Integer voiceDurationSeconds,
        Boolean isRead,
        LocalDateTime sentAt,
        ListingContextResponse listing
) {
}
