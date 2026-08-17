package co.istad.projectpracticum.phsardigital.features.seller.application;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seller_applications")
@Getter
@Setter
public class SellerApplication extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    // Keycloak sub of the applicant (they're a USER at apply time)
    @Column(name = "applicant_id", nullable = false)
    private String applicantId;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "business_type", length = 100)
    private String businessType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String province;

    /**
     * Shop logo the applicant picked. A public image, unlike the supporting
     * documents in {@link SellerApplicationDocument} — a logo is meant to be seen,
     * an identity card is not. Copied onto the {@code SellerProfile} on approval.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "logo_file_id")
    private FileUpload logoFile;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "google_map_url", columnDefinition = "TEXT")
    private String googleMapUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    // review outcome
    @Column(name = "reviewed_by")
    private String reviewedBy;          // admin's Keycloak sub

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_note", columnDefinition = "TEXT")
    private String rejectionNote;
}