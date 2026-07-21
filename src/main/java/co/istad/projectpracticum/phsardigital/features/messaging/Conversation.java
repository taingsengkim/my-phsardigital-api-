package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"participant_a", "participant_b"}))
@Getter
@Setter
public class Conversation extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(name = "participant_a", nullable = false)
    private String participantA;

    @Column(name = "participant_b", nullable = false)
    private String participantB;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> messages = new ArrayList<>();
}