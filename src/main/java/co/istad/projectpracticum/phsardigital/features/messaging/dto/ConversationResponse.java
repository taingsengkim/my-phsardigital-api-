package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param lastListing the product most recently asked about in this thread, or null if
 *                    it was never opened from a listing. Lets the inbox show a shop
 *                    which enquiry a row is about before they open it — the difference
 *                    between a list of names and a list of questions.
 *
 *                    <p>Derived from the messages rather than stored on the
 *                    conversation, so it always agrees with what the thread itself
 *                    shows. It is the newest message carrying a listing, which is not
 *                    necessarily the newest message.
 */
public record ConversationResponse(
        UUID uuid,
        String otherUserId,
        String otherUserName,
        String otherUserAvatar,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount,
        ListingContextResponse lastListing
) {
}