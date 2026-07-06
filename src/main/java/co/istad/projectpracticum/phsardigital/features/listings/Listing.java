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

    @Column(nullable = false)
    private Double price;

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

    @OneToMany(mappedBy = "listing", orphanRemoval = true)
    private List<ListingImage> images = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "seller_profile_uuid", nullable = false)
    private SellerProfile sellerProfile;


    @OneToMany( mappedBy = "listing" , orphanRemoval = true, cascade = CascadeType.ALL)
    private List<ListingAttribute> listingAttributes ;


}
