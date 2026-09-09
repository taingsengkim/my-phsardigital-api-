package co.istad.projectpracticum.phsardigital.features.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * A page of a thread, newest first.
     *
     * <p>The product card and its thumbnail are fetched alongside, because rendering a
     * thread reads both for every message that has one — thirty lazy loads per screen
     * otherwise. Both are to-one associations, so the join does not force Hibernate to
     * paginate in memory the way fetching a collection would.
     */
    @EntityGraph(attributePaths = {"listing", "listing.thumbnailFile", "voiceFile"})
    Page<Message> findByConversation_UuidOrderBySentAtDesc(UUID conversationUuid, Pageable pageable);

    /** Whether any message still plays this recording — backs {@code MessagingFileReferences}. */
    boolean existsByVoiceFile_ObjectName(String objectName);

    /**
     * The most recent message in a thread that names a product, for the inbox preview.
     *
     * <p>Separate from the last-message lookup because the two are usually different
     * rows: an enquiry opens with the product card and the conversation carries on
     * without it. Paged rather than returning one row so the caller can ask for exactly
     * one and Hibernate can stop there.
     */
    @Query("SELECT m FROM Message m "
            + "WHERE m.conversation.uuid = :conversationUuid AND m.listing IS NOT NULL "
            + "ORDER BY m.sentAt DESC")
    Page<Message> findLatestWithListing(@Param("conversationUuid") UUID conversationUuid,
                                        Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m " +
            "WHERE m.conversation.uuid = :conversationUuid " +
            "AND m.senderId <> :userId AND m.isRead = false")
    long countUnread(@Param("conversationUuid") UUID conversationUuid,
                     @Param("userId") String userId);

    @Query("SELECT COUNT(m) FROM Message m " +
            "WHERE (m.conversation.participantA = :userId OR m.conversation.participantB = :userId) " +
            "AND m.senderId <> :userId AND m.isRead = false")
    long countAllUnreadForUser(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true " +
            "WHERE m.conversation.uuid = :conversationUuid " +
            "AND m.senderId <> :userId AND m.isRead = false")
    void markAllRead(@Param("conversationUuid") UUID conversationUuid,
                     @Param("userId") String userId);
}