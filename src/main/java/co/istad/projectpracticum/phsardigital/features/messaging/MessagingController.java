package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.features.messaging.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;

    @GetMapping
    public List<ConversationResponse> myConversations() {
        return messagingService.getMyConversations();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse start(@Valid @RequestBody StartConversationRequest request) {
        return messagingService.startConversation(request);
    }

    @GetMapping("/{uuid}/messages")
    public Page<MessageResponse> messages(
            @PathVariable UUID uuid,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "30") int pageSize) {
        return messagingService.getMessages(uuid, pageNumber, pageSize);
    }

    @PostMapping("/{uuid}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable UUID uuid,
                                @Valid @RequestBody SendMessageRequest request) {
        return messagingService.sendMessage(uuid, request);
    }

    @PatchMapping("/{uuid}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID uuid) {
        messagingService.markAsRead(uuid);
    }
}