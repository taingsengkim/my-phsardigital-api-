package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.features.messaging.dto.*;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface MessagingService {

    /** My conversations, newest activity first. */
    List<ConversationResponse> getMyConversations();

    /** Start a conversation with someone (returns existing if it already exists). */
    ConversationResponse startConversation(StartConversationRequest request);

    /** Messages in a thread (paged, newest first). */
    Page<MessageResponse> getMessages(UUID conversationUuid, int pageNumber, int pageSize);

    /** Send a message into a thread. */
    MessageResponse sendMessage(UUID conversationUuid, SendMessageRequest request);

    /** Mark all incoming messages in a thread as read. */
    void markAsRead(UUID conversationUuid);
}