package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "conversation_uuid", nullable = false)
    private Conversation conversation;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    /**
     * The listing this message was sent about, when the sender opened the chat from a
     * product page. Null for ordinary conversation.
     *
     * <p>It hangs off the message rather than the conversation deliberately. A
     * conversation is one pair of people — the table enforces that — so a buyer who
     * asks about a phone today and a charger next week is in the same thread both
     * times. Recording the product per message keeps each enquiry attached to the point
     * it was made, which is what lets the thread be read back sensibly later; putting it
     * on the conversation could only ever remember the most recent one.
     *
     * <p>Lazy because most messages have no listing and the ones that do are only
     * needed when a thread is actually rendered.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_uuid")
    private Listing listing;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();
}