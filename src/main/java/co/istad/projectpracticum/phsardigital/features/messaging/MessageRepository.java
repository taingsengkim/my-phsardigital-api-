package co.istad.projectpracticum.phsardigital.features.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversation_UuidOrderBySentAtDesc(UUID conversationUuid, Pageable pageable);

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