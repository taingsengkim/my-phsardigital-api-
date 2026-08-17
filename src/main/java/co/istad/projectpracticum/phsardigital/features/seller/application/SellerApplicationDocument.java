package co.istad.projectpracticum.phsardigital.features.seller.application;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seller_application_documents")
@Getter
@Setter
public class SellerApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "application_uuid", nullable = false)
    private SellerApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 100)
    private SellerDocumentType docType;

    @Column(name = "object_name", nullable = false)
    private String objectName;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();
}