package co.istad.sengkim.phsardigital.features.listing_images.dto;

import co.istad.sengkim.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity(name = "listing_images")
public class ListingImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    private String imageUrl;
    private Integer sortOrder;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_uuid")
    private Listing listing;
    private Boolean isPrimary;
}
