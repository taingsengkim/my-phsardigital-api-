package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "seller_profiles")
@NoArgsConstructor
public class SellerProfile {
    public SellerProfile(String userId) {
        this.sellerId = userId;
    }
    @Id
    private String sellerId;

    @Column(name = "business_name", length = 255)
    private String businessName;

    @Column(name = "business_type", length = 100)
    private String businessType;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 1000)
    private String biography;

    @ElementCollection
    @CollectionTable(
            name = "seller_social_links",
            joinColumns = @JoinColumn(name = "seller_id")
    )
    @Column(name = "social_link")
    private List<String> socialLink;

    @Column(name = "is_active")
    private Boolean isActive = false;   // true only after admin approval

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_note", columnDefinition = "TEXT")
    private String rejectionNote;

    @OneToMany(mappedBy = "sellerProfile")
    private List<Listing> listings;
}
