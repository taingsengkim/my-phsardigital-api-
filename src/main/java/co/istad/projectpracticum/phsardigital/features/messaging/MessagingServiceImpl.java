package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.messaging.dto.*;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionService;
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
    private final FileUploadService fileUploadService;
    private final SellerAccessGuard sellerAccessGuard;
    private final SubscriptionService subscriptionService;
    private final ListingRepository listingRepository;
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
        requireChatAllowedIfSeller(me);

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
        requireChatAllowedIfSeller(me);

        Message message = new Message();
        message.setConversation(conversation);
        message.setSenderId(me);
        message.setBody(request.body());
        message.setIsRead(false);
        message.setListing(resolveListing(request.listingUuid(), conversation));

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
        // The same response the recipient was pushed, rather than building a second
        // identical one — which now costs a listing and a thumbnail lookup as well.
        return response;
    }

    @Override
    @Transactional
    public void markAsRead(UUID conversationUuid) {
        String me = AuthUtils.extractUserId();
        requireParticipant(conversationUuid, me);
        messageRepository.markAllRead(conversationUuid, me);
    }

    // ---------- helpers ----------

    /**
     * Gates <em>sending</em> on the sender's subscription, and only when the sender
     * is a shop.
     *
     * <p>Buyers are never charged to talk to a shop, so the check is skipped for
     * anybody without a seller profile — otherwise a customer's first question would
     * bounce. Reading a thread is left open in both directions too: a lapsed seller
     * should be able to see the enquiries they are missing, which is the whole
     * argument for renewing.
     */
    private void requireChatAllowedIfSeller(String userId) {
        if (!sellerAccessGuard.isSeller(userId)) {
            return;
        }
        sellerAccessGuard.requireActiveSeller(userId);
        subscriptionService.requireChatAllowed(userId);
    }

    /**
     * Resolves the product a message is about, and refuses one that has no business
     * being there.
     *
     * <p>The listing has to belong to one of the two people in the thread. Without that
     * check anybody could attach any shop's product to any conversation, which would
     * let a chat window render a rival's listing — or be used to push a product card at
     * someone who never asked about it. Ownership is the whole of the rule: whether the
     * listing is currently buyable is deliberately not checked, because asking a shop
     * about something that just sold out is exactly when a buyer most wants to write.
     *
     * @return the listing, or null when the message names none
     */
    private Listing resolveListing(UUID listingUuid, Conversation conversation) {
        if (listingUuid == null) {
            return null;
        }

        Listing listing = listingRepository.findById(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Listing not found."));

        String owner = listing.getSellerProfile().getSellerId();
        if (!owner.equals(conversation.getParticipantA())
                && !owner.equals(conversation.getParticipantB())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That listing does not belong to either person in this conversation.");
        }
        return listing;
    }

    /** The product card for a message, or null when it names no product. */
    private ListingContextResponse toListingContext(Listing listing) {
        if (listing == null) {
            return null;
        }
        String thumbnailUrl = listing.getThumbnailFile() == null
                ? null
                : fileUploadService.getPreviewUrl(listing.getThumbnailFile());
        return ListingContextResponse.of(listing, thumbnailUrl);
    }

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

        // The newest message that named a product, which is rarely the newest message:
        // an enquiry opens with the card and the conversation carries on without it.
        Page<Message> latestWithListing = messageRepository
                .findLatestWithListing(c.getUuid(), PageRequest.of(0, 1));
        ListingContextResponse lastListing = latestWithListing.hasContent()
                ? toListingContext(latestWithListing.getContent().getFirst().getListing())
                : null;

        return new ConversationResponse(
                c.getUuid(),
                otherId,
                other != null ? other.getFullName() : null,
                avatarUrl(other),
                lastBody,
                lastAt,
                unread,
                lastListing
        );
    }

    private String avatarUrl(UserProfile profile) {
        if (profile == null || profile.getAvatarFile() == null) {
            return null;
        }
        return fileUploadService.getPreviewUrl(profile.getAvatarFile());
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
                m.getSentAt(),
                toListingContext(m.getListing())
        );
    }
}