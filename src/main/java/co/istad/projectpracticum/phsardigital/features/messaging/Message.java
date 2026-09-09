package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
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

    /**
     * The typed text, or null on a voice note that came with no caption.
     *
     * <p>Nullable since voice messages arrived. Hibernate's {@code update} never relaxes
     * an existing {@code NOT NULL}, so a database created before this change needs one
     * statement run against it by hand:
     * {@code ALTER TABLE messages ALTER COLUMN body DROP NOT NULL;} — without it every
     * voice note fails on insert.
     */
    @Column(columnDefinition = "TEXT")
    private String body;

    /**
     * The recording, when this message is a voice note.
     *
     * <p>Lives in the private bucket and is answered as an expiring presigned URL, so a
     * message nobody is authorised to read cannot be listened to by anyone who happens
     * upon the object name.
     *
     * <p>Eager because a thread renders every bubble it loads, and a lazy proxy here
     * would cost a query per voice note at exactly the moment they are all needed.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "voice_file_id")
    private FileUpload voiceFile;

    /**
     * How long the recording runs, in whole seconds, as reported by the recorder that
     * made it.
     *
     * <p>Client-supplied, and deliberately so: measuring it server-side means decoding
     * the audio, which is a media library this API does not carry for a number that only
     * draws a duration label. It is bounded on the way in, so a wrong value can mislabel
     * a bubble but cannot break a layout. Never treat it as evidence of anything.
     */
    @Column(name = "voice_duration_seconds")
    private Integer voiceDurationSeconds;

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