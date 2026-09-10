package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pushed when somebody opens a thread and its incoming messages become read.
 *
 * <p>Goes to both people, because each learns something different from it. The reader's
 * <em>other</em> devices clear an unread badge that is now wrong — a phone and a laptop
 * signed in to one account have no other way to find out. The sender learns their
 * messages were seen, which is the read receipt.
 *
 * <p>Carries no message list. A client that knows the thread was read at a given moment
 * can mark what it already holds; sending the uuids would grow without bound on a thread
 * left unread for a long time, to say something the client can work out for itself.
 *
 * @param readerId the person who did the reading — the receiving client compares it
 *                 against its own id to tell a badge update from a read receipt
 */
public record ConversationReadEvent(
        UUID conversationUuid,
        String readerId,
        LocalDateTime readAt
) {
}
