package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "listing_attributes",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"listing_uuid", "key"})
        })
@Getter
@Setter
public class ListingAttribute extends BasedEntity {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID)
    private UUID uuid ;

    @NotBlank(message = "Attribute key is required")
    @Column(length = 100, nullable = false)
    private String key ;

    @NotBlank(message = "Attribute key is required")
    @Column(length = 100, nullable = false)
    private String value ;

    @Column(name = "sort_order")
    private Integer sortOrder = 0 ;

    @ManyToOne
    @JoinColumn( name = "listing_uuid")
    private Listing listing ;

}
