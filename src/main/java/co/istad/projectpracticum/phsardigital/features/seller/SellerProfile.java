package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "seller_profiles")
@Getter
@Setter
@NoArgsConstructor
public class SellerProfile extends BasedEntity {
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

    /**
     * Shop details. These are declared on both {@code SellerProfileResponse} and
     * {@code SellerProfileUpdateRequest}, so without them the mapper silently
     * dropped every write and always answered null.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String province;

    /**
     * Shop logo, stored in the public bucket so it can be rendered straight from an
     * {@code <img>} tag. Held as a reference rather than a bare object name so the
     * mapper can pick the right URL scheme and so deleting the file detaches it —
     * see {@code SellerProfileFileListener}.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "logo_file_id")
    private FileUpload logoFile;

    /** The banner across the top of the shop page, held on the same terms as the logo. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cover_file_id")
    private FileUpload coverFile;

    /**
     * Shop pin, matching the precision {@code Address} uses for delivery
     * coordinates. Stored as the pair rather than only a Google Maps link so the
     * shop can be placed on a map and searched by distance without parsing a URL.
     */
    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    /** The shop's Google Maps link, kept alongside the pin for one-tap directions. */
    @Column(name = "google_map_url", columnDefinition = "TEXT")
    private String googleMapUrl;

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

    // Suspension, kept apart from reviewedBy/reviewedAt above: those record the
    // application decision that created this shop, and overwriting them on a
    // suspension would lose who approved it in the first place.

    @Column(name = "suspended_by")
    private String suspendedBy;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    @OneToMany(mappedBy = "sellerProfile")
    private List<Listing> listings;
}