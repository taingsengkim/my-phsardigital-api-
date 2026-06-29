package co.istad.projectpracticum.phsardigital.features.listings.listing_images;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
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
    private String objectName;
    private Integer sortOrder;
    @ManyToOne()
    @JoinColumn(name = "listing_uuid")
    private Listing listing;
    private Boolean isPrimary;
}
