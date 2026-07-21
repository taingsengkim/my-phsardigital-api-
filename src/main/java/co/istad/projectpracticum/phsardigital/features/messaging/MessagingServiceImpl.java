package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.messaging.dto.*;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingServiceImpl implements MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserProfileRepository userProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;
    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations() {
        String me = AuthUtils.extractUserId();
        List<ConversationResponse> result = new ArrayList<>();
        for (Conversation c : conversationRepository.findAllForUser(me)) {
            result.add(toResponse(c, me));
        }
        return result;
    }

    @Override
    @Transactional
    public ConversationResponse startConversation(StartConversationRequest request) {
        String me = AuthUtils.extractUserId();
        String other = request.participantId();

        if (me.equals(other)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot start a conversation with yourself.");
        }
        if (!userProfileRepository.existsById(other)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }

        Conversation conversation = findOrCreate(me, other);
        return toResponse(conversation, me);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationUuid, int pageNumber, int pageSize) {
        String me = AuthUtils.extractUserId();
        requireParticipant(conversationUuid, me);

        return messageRepository
                .findByConversation_UuidOrderBySentAtDesc(
                        conversationUuid, PageRequest.of(pageNumber, pageSize))
                .map(this::toMessageResponse);
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(UUID conversationUuid, SendMessageRequest request) {
        String me = AuthUtils.extractUserId();
        Conversation conversation = requireParticipant(conversationUuid, me);

        Message message = new Message();
        message.setConversation(conversation);
        message.setSenderId(me);
        message.setBody(request.body());
        message.setIsRead(false);

        Message saved = messageRepository.save(message);

        // touch the conversation so it sorts to the top of the inbox
        conversationRepository.save(conversation);
        MessageResponse response = toMessageResponse(saved);

        // ---- REAL-TIME PUSH ----
        String recipientId = conversation.getParticipantA().equals(me)
                ? conversation.getParticipantB()
                : conversation.getParticipantA();

        log.info(">>> PUSHING to recipient: {}", recipientId);

        messagingTemplate.convertAndSendToUser(
                recipientId,
                "/queue/messages",
                response
        );
        return toMessageResponse(saved);
    }

    @Override
    @Transactional
    public void markAsRead(UUID conversationUuid) {
        String me = AuthUtils.extractUserId();
        requireParticipant(conversationUuid, me);
        messageRepository.markAllRead(conversationUuid, me);
    }

    // ---------- helpers ----------

    /** Normalizes the pair (a < b) so one pair == one conversation. */
    private Conversation findOrCreate(String me, String other) {
        String a = me.compareTo(other) < 0 ? me : other;
        String b = me.compareTo(other) < 0 ? other : me;

        return conversationRepository.findByParticipantAAndParticipantB(a, b)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setParticipantA(a);
                    c.setParticipantB(b);
                    return conversationRepository.save(c);
                });
    }

    private Conversation requireParticipant(UUID conversationUuid, String userId) {
        Conversation c = conversationRepository.findById(conversationUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Conversation not found."));
        if (!c.getParticipantA().equals(userId) && !c.getParticipantB().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not part of this conversation.");
        }
        return c;
    }

    private ConversationResponse toResponse(Conversation c, String me) {
        String otherId = c.getParticipantA().equals(me)
                ? c.getParticipantB() : c.getParticipantA();

        UserProfile other = userProfileRepository.findById(otherId).orElse(null);

        // last message
        Page<Message> last = messageRepository
                .findByConversation_UuidOrderBySentAtDesc(c.getUuid(), PageRequest.of(0, 1));
        String lastBody = last.hasContent() ? last.getContent().getFirst().getBody() : null;
        var lastAt = last.hasContent() ? last.getContent().getFirst().getSentAt() : null;

        long unread = messageRepository.countUnread(c.getUuid(), me);

        return new ConversationResponse(
                c.getUuid(),
                otherId,
                other != null ? other.getFullName() : null,
                other != null ? other.getAvatarUrl() : null,
                lastBody,
                lastAt,
                unread
        );
    }

    private MessageResponse toMessageResponse(Message m) {
        UserProfile sender = userProfileRepository.findById(m.getSenderId()).orElse(null);
        return new MessageResponse(
                m.getUuid(),
                m.getConversation().getUuid(),
                m.getSenderId(),
                sender != null ? sender.getFullName() : null,
                m.getBody(),
                m.getIsRead(),
                m.getSentAt()
        );
    }
}