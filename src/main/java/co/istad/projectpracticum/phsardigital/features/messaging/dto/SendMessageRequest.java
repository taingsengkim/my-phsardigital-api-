package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * @param listingUuid the product the sender is writing about, when they opened the chat
 *                    from a listing page. Optional, and normally sent only on the first
 *                    message of an enquiry — the client keeps sending it only if the
 *                    buyer switches to asking about something else. The listing must
 *                    belong to one of the two people in the conversation.
 */
public record SendMessageRequest(
        @NotBlank(message = "Message cannot be empty")
        String body,

        UUID listingUuid
) {
}