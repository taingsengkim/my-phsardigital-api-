package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.messaging.dto.SendMessageRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionService;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessagingListingContextTest {

    private static final String BUYER = "buyer-1";
    private static final String SHOP = "shop-1";
    private static final UUID CONVERSATION = UUID.randomUUID();

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private FileUploadService fileUploadService;
    @Mock
    private SellerAccessGuard sellerAccessGuard;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ListingRepository listingRepository;
    @InjectMocks
    private MessagingServiceImpl service;

    @Test
    void aMessageSentFromAListingCarriesThatProduct() {
        Conversation conversation = conversation();
        Listing listing = listing(SHOP, "iPhone 13", "899.00", "799.00");
        givenConversation(conversation);
        when(listingRepository.findById(listing.getUuid())).thenReturn(Optional.of(listing));
        when(messageRepository.save(any(Message.class))).thenAnswer(call -> call.getArgument(0));

        try (MockedStatic<AuthUtils> auth = authenticated(BUYER)) {
            var sent = service.sendMessage(CONVERSATION,
                    new SendMessageRequest("Is this still available?", listing.getUuid()));

            assertThat(sent.listing()).isNotNull();
            assertThat(sent.listing().uuid()).isEqualTo(listing.getUuid());
            assertThat(sent.listing().title()).isEqualTo("iPhone 13");
            // The card shows what it costs now, with the struck-through original.
            assertThat(sent.listing().price()).isEqualByComparingTo("799.00");
            assertThat(sent.listing().fullPrice()).isEqualByComparingTo("899.00");
        }
    }

    @Test
    void anOrdinaryMessageCarriesNoProductAndCostsNoLookup() {
        givenConversation(conversation());
        when(messageRepository.save(any(Message.class))).thenAnswer(call -> call.getArgument(0));

        try (MockedStatic<AuthUtils> auth = authenticated(BUYER)) {
            var sent = service.sendMessage(CONVERSATION, new SendMessageRequest("Thanks!", null));
            assertThat(sent.listing()).isNull();
        }

        verify(listingRepository, never()).findById(any());
    }

    @Test
    void aProductBelongingToNeitherPartyIsRefused() {
        // Otherwise anyone could attach any shop's listing to any thread, and push a
        // rival's product card into a conversation that never mentioned it.
        Conversation conversation = conversation();
        Listing someoneElses = listing("other-shop", "Rival Phone", "500.00", "500.00");
        givenConversation(conversation);
        when(listingRepository.findById(someoneElses.getUuid()))
                .thenReturn(Optional.of(someoneElses));

        try (MockedStatic<AuthUtils> auth = authenticated(BUYER)) {
            assertThatThrownBy(() -> service.sendMessage(CONVERSATION,
                    new SendMessageRequest("look at this", someoneElses.getUuid())))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }

        verify(messageRepository, never()).save(any());
    }

    @Test
    void anUnknownProductIsNotFound() {
        givenConversation(conversation());
        UUID missing = UUID.randomUUID();
        when(listingRepository.findById(missing)).thenReturn(Optional.empty());

        try (MockedStatic<AuthUtils> auth = authenticated(BUYER)) {
            assertThatThrownBy(() -> service.sendMessage(CONVERSATION,
                    new SendMessageRequest("hi", missing)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Test
    void askingAboutASoldOutProductIsStillAllowed() {
        // Precisely when a buyer most wants to write to the shop.
        Conversation conversation = conversation();
        Listing soldOut = listing(SHOP, "Sold Out Phone", "899.00", "899.00");
        soldOut.setStatus(ListingStatus.SOLD_OUT);
        givenConversation(conversation);
        when(listingRepository.findById(soldOut.getUuid())).thenReturn(Optional.of(soldOut));
        when(messageRepository.save(any(Message.class))).thenAnswer(call -> call.getArgument(0));

        try (MockedStatic<AuthUtils> auth = authenticated(BUYER)) {
            var sent = service.sendMessage(CONVERSATION,
                    new SendMessageRequest("Any restock?", soldOut.getUuid()));

            // The card still renders, carrying the status so the client can mark it.
            assertThat(sent.listing().status()).isEqualTo(ListingStatus.SOLD_OUT);
        }
    }

    @Test
    void theInboxShowsTheProductLastAskedAboutNotTheLastMessage() {
        // An enquiry opens with the product card and the chat carries on without it, so
        // the newest message and the newest product message are different rows.
        Conversation conversation = conversation();
        Listing listing = listing(SHOP, "iPhone 13", "899.00", "799.00");

        Message chitChat = message("see you then", null);
        Message enquiry = message("Is this available?", listing);

        when(conversationRepository.findAllForUser(SHOP)).thenReturn(List.of(conversation));
        when(messageRepository.findByConversation_UuidOrderBySentAtDesc(eq(CONVERSATION), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(chitChat)));
        when(messageRepository.findLatestWithListing(eq(CONVERSATION), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(enquiry)));
        when(messageRepository.countUnread(CONVERSATION, SHOP)).thenReturn(2L);

        try (MockedStatic<AuthUtils> auth = authenticated(SHOP)) {
            var inbox = service.getMyConversations();

            assertThat(inbox).singleElement().satisfies(row -> {
                assertThat(row.lastMessage()).isEqualTo("see you then");
                assertThat(row.lastListing()).isNotNull();
                assertThat(row.lastListing().title()).isEqualTo("iPhone 13");
                assertThat(row.unreadCount()).isEqualTo(2L);
            });
        }
    }

    @Test
    void aThreadThatNeverMentionedAProductHasNoCard() {
        Conversation conversation = conversation();
        when(conversationRepository.findAllForUser(SHOP)).thenReturn(List.of(conversation));
        when(messageRepository.findByConversation_UuidOrderBySentAtDesc(eq(CONVERSATION), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message("hello", null))));
        when(messageRepository.findLatestWithListing(eq(CONVERSATION), any(Pageable.class)))
                .thenReturn(Page.empty());

        try (MockedStatic<AuthUtils> auth = authenticated(SHOP)) {
            assertThat(service.getMyConversations())
                    .singleElement()
                    .satisfies(row -> assertThat(row.lastListing()).isNull());
        }
    }

    // ---------- fixtures ----------

    private void givenConversation(Conversation conversation) {
        when(conversationRepository.findById(CONVERSATION)).thenReturn(Optional.of(conversation));
    }

    private static MockedStatic<AuthUtils> authenticated(String userId) {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(userId);
        return auth;
    }

    private static Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setUuid(CONVERSATION);
        // findOrCreate normalises the pair so a < b.
        conversation.setParticipantA(BUYER.compareTo(SHOP) < 0 ? BUYER : SHOP);
        conversation.setParticipantB(BUYER.compareTo(SHOP) < 0 ? SHOP : BUYER);
        return conversation;
    }

    private static Message message(String body, Listing listing) {
        Message message = new Message();
        message.setUuid(UUID.randomUUID());
        message.setConversation(conversation());
        message.setSenderId(BUYER);
        message.setBody(body);
        message.setIsRead(false);
        message.setListing(listing);
        message.setSentAt(LocalDateTime.now());
        return message;
    }

    private static Listing listing(String sellerId, String title, String fullPrice, String effective) {
        SellerProfile shop = new SellerProfile();
        shop.setSellerId(sellerId);

        Listing listing = new Listing();
        listing.setUuid(UUID.randomUUID());
        listing.setTitle(title);
        listing.setSlug(title.toLowerCase().replace(' ', '-'));
        listing.setFullPrice(new BigDecimal(fullPrice));
        if (!fullPrice.equals(effective)) {
            listing.setDiscountPrice(new BigDecimal(effective));
        }
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setSellerProfile(shop);
        return listing;
    }
}
