package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * One product's answer to a spec — "screen_size" is "6.7" — where the question is the
 * {@link CategoryAttribute} its category declares.
 *
 * <p>The value stays text whatever the declared type is: a spec table prints it. What the
 * definition buys is that it was checked before it got here, and that the storefront knows
 * to print "6.7 in" under a Display heading rather than "screen_size: 6.7" in a flat list.
 */
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

    /**
     * The category attribute this answers, or null for a spec the seller added that the
     * category does not define — which stays allowed, so listings written before their
     * category had a schema keep working.
     *
     * <p>Not the source of {@link #key}: the key is copied at write time so an attribute
     * survives its definition being soft-deleted or re-pointed. This link is what the
     * label, unit and group are read through.
     */
    @ManyToOne
    @JoinColumn(name = "category_attribute_uuid")
    private CategoryAttribute definition;

}
