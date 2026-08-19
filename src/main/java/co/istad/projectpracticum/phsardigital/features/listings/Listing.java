package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.ListingImage;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "listings",  uniqueConstraints = {
        @UniqueConstraint(columnNames = {"seller_profile_uuid", "slug"})
})
@Getter
@Setter
public class Listing extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne()
    @JoinColumn(name = "category_uuid", nullable = false)
    private Category category;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false , unique = true)
    private String slug;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * The list price. Still the {@code price} column: renaming it under
     * {@code ddl-auto: update} would add an empty {@code full_price} beside the
     * populated {@code price} rather than move anything.
     */
    @Column(name = "price", nullable = false)
    private Double fullPrice;

    /** The sale price, or null when the listing is not discounted. */
    @Column(name = "discount_price")
    private Double discountPrice;

    @Column(nullable = false)
    private Integer stockQty;

    @Enumerated(EnumType.STRING)
    private ListingStatus status;

    @Column(nullable = false)
    private Boolean isFeatured;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "thumbnail_file_id", nullable = false)
    private FileUpload thumbnailFile;


    @Column(nullable = false)
    private Integer sold;

    /**
     * Ordered here rather than in one query, so every path that loads a listing gets
     * the gallery in the seller's order. {@code sortOrder} was stored and echoed back
     * but never ordered anything, which left the gallery in whatever sequence the
     * database happened to return — arbitrary, and liable to change after an update.
     *
     * <p>Rows written before this carry a null {@code sortOrder} and sort last, which
     * is the harmless end of the gallery; the first reorder normalises them.
     */
    @OneToMany(mappedBy = "listing", orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ListingImage> images = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "seller_profile_uuid", nullable = false)
    private SellerProfile sellerProfile;


    @OneToMany( mappedBy = "listing" , orphanRemoval = true, cascade = CascadeType.ALL)
    private List<ListingAttribute> listingAttributes ;

    // Moderation. Shaped after SellerApplication's reviewedBy/reviewedAt/rejectionNote
    // so "who did this and why" is answered the same way across the admin surface.

    @Column(name = "moderated_by")
    private String moderatedBy;

    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    @Column(name = "moderation_reason", columnDefinition = "TEXT")
    private String moderationReason;

    /**
     * What the listing was before it was suspended, so restoring puts it back rather
     * than publishing it. A restore that always chose {@code ACTIVE} would resurrect
     * an archived listing, which is the plan-limit bypass {@code update} already
     * guards against.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_suspension", length = 20)
    private ListingStatus statusBeforeSuspension;


//    @Column(name = "average_rating")
//    private Double averageRating = 0.0;
//
//    @Column(name = "review_count")
//    private Integer reviewCount = 0;

    /**
     * What a buyer actually pays. Not a {@code get...} accessor, so neither Hibernate
     * nor MapStruct mistakes it for a persistent property.
     */
    public Double effectivePrice() {
        return discountPrice != null ? discountPrice : fullPrice;
    }

}
