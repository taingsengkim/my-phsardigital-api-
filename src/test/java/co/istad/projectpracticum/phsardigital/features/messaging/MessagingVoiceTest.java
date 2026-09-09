package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.messaging.dto.MessageResponse;
import co.istad.projectpracticum.phsardigital.features.messaging.dto.SendMessageRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionService;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

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
class MessagingVoiceTest {

    private static final String BUYER = "buyer-1";
    private static final String SHOP = "shop-1";
    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final String OBJECT = "voice/abc.webm";

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private FileUploadService fileUploadService;
    @Mock private SellerAccessGuard sellerAccessGuard;
    @Mock private SubscriptionService subscriptionService;
    @Mock private ListingRepository listingRepository;
    @InjectMocks private MessagingServiceImpl service;

    @Test
    @DisplayName("a voice note is answered as VOICE, with a signed link and its duration")
    void sendsAVoiceNote() {
        givenConversation();
        FileUpload recording = audioFile("audio/webm");
        when(fileUploadService.requireOwnedFile(OBJECT, BUYER)).thenReturn(recording);
        when(fileUploadService.getPreviewUrl(recording)).thenReturn("https://minio/signed?X-Amz=1");

        try (MockedStatic<AuthUtils> auth = authenticated()) {
            MessageResponse sent = service.sendMessage(CONVERSATION,
                    new SendMessageRequest(null, OBJECT, 12, null));

            assertThat(sent.type()).isEqualTo(MessageType.VOICE);
            assertThat(sent.voiceUrl()).isEqualTo("https://minio/signed?X-Amz=1");
            assertThat(sent.voiceDurationSeconds()).isEqualTo(12);
            assertThat(sent.body()).isNull();
        }
    }

    @Test
    @DisplayName("a text message is still TEXT and carries no audio")
    void textIsUnaffected() {
        givenConversation();

        try (MockedStatic<AuthUtils> auth = authenticated()) {
            MessageResponse sent = service.sendMessage(CONVERSATION,
                    new SendMessageRequest("Still available?", null, null, null));

            assertThat(sent.type()).isEqualTo(MessageType.TEXT);
            assertThat(sent.voiceUrl()).isNull();
            assertThat(sent.voiceDurationSeconds()).isNull();
            verify(fileUploadService, never()).requireOwnedFile(any(), any());
        }
    }

    @Test
    @DisplayName("a caption sent with a recording is kept")
    void keepsTheCaption() {
        givenConversation();
        when(fileUploadService.requireOwnedFile(OBJECT, BUYER)).thenReturn(audioFile("audio/mp4"));

        try (MockedStatic<AuthUtils> auth = authenticated()) {
            MessageResponse sent = service.sendMessage(CONVERSATION,
                    new SendMessageRequest("about the red one", OBJECT, 8, null));

            assertThat(sent.type()).isEqualTo(MessageType.VOICE);
            assertThat(sent.body()).isEqualTo("about the red one");
        }
    }

    @Test
    @DisplayName("whitespace sent as a caption is stored as nothing")
    void blankCaptionBecomesNull() {
        givenConversation();
        when(fileUploadService.requireOwnedFile(OBJECT, BUYER)).thenReturn(audioFile("audio/webm"));

        try (MockedStatic<AuthUtils> auth = authenticated()) {
            assertThat(service.sendMessage(CONVERSATION,
                    new SendMessageRequest("   ", OBJECT, 8, null)).body()).isNull();
        }
    }

    @Test
    @DisplayName("a recording the sender does not own is refused before anything is saved")
    void refusesSomebodyElsesRecording() {
        givenConversation();
        when(fileUploadService.requireOwnedFile(OBJECT, BUYER))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your file."));

        try (MockedStatic<AuthUtils> auth = authenticated()) {
            assertThatThrownBy(() -> service.sendMessage(CONVERSATION,
                    new SendMessageRequest(null, OBJECT, 5, null)))
                    .isInstanceOf(ResponseStatusException.class);

            verify(messageRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("a file the sender owns but that is not audio is refused")
    void refusesANonAudioFileTheSenderOwns() {
        givenConversation();
        // Their own uploaded business licence: ownership passes, so only the type check
        // stops it being handed to the other party as a presigned link.
        when(fileUploadService.requireOwnedFile(OBJECT, BUYER))
                .thenReturn(audioFile("application/pdf"));

        try (MockedStatic<AuthUtils> auth = authenticated()) {
            assertThatThrownBy(() -> service.sendMessage(CONVERSATION,
                    new SendMessageRequest(null, OBJECT, 5, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not a voice recording");

            verify(messageRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("a file with no recorded type is refused rather than assumed to be audio")
    void refusesAnUntypedFile() {
        givenConversation();
        when(fileUploadService.requireOwnedFile(OBJECT, BUYER)).thenReturn(audioFile(null));

        try (MockedStatic<AuthUtils> auth = authenticated()) {
            assertThatThrownBy(() -> service.sendMessage(CONVERSATION,
                    new SendMessageRequest(null, OBJECT, 5, null)))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    @DisplayName("the recipient is pushed the same payload the sender was answered")
    void pushesTheSamePayload() {
        givenConversation();
        FileUpload recording = audioFile("audio/webm");
        when(fileUploadService.requireOwnedFile(OBJECT, BUYER)).thenReturn(recording);
        when(fileUploadService.getPreviewUrl(recording)).thenReturn("https://minio/signed");

        try (MockedStatic<AuthUtils> auth = authenticated()) {
            MessageResponse sent = service.sendMessage(CONVERSATION,
                    new SendMessageRequest(null, OBJECT, 12, null));

            verify(messagingTemplate).convertAndSendToUser(eq(SHOP), eq("/queue/messages"), eq(sent));
        }
    }

    // ---------------------------------------------------------------- helpers

    private void givenConversation() {
        Conversation conversation = new Conversation();
        conversation.setUuid(CONVERSATION);
        conversation.setParticipantA(BUYER);
        conversation.setParticipantB(SHOP);
        when(conversationRepository.findById(CONVERSATION)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(call -> call.getArgument(0));
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
    }

    private static FileUpload audioFile(String contentType) {
        FileUpload file = new FileUpload();
        file.setObjectName(OBJECT);
        file.setContentType(contentType);
        return file;
    }

    private static MockedStatic<AuthUtils> authenticated() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(BUYER);
        return auth;
    }
}
