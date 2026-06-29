package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.ListingImage;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "listings")
@Getter
@Setter
public class Listing extends BasedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    @ManyToOne()
    @JoinColumn(name = "category_uuid")
    private Category category;
    private String title;
    private String slug;
    private String description;
    private Double price;
    private Integer stockQty;
    @Enumerated(EnumType.STRING)
    private ListingStatus status;
    private Boolean isFeatured;
    private String thumbnailObjectName;
    private Integer soldCount;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ListingImage> imagesList = new ArrayList<>();

    @ManyToOne
    private SellerProfile sellerProfile;
}
