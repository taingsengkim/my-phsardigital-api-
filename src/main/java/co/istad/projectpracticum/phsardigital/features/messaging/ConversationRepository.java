package co.istad.projectpracticum.phsardigital.features.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByParticipantAAndParticipantB(String a, String b);

    @Query("SELECT c FROM Conversation c " +
            "WHERE c.participantA = :userId OR c.participantB = :userId " +
            "ORDER BY c.lastModifiedAt DESC")
    List<Conversation> findAllForUser(@Param("userId") String userId);
}