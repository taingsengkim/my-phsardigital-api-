package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_items")
@Getter
@Setter
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "purchase_uuid", nullable = false)
    private Purchase purchase;

    @ManyToOne
    @JoinColumn(name = "listing_uuid", nullable = false)
    private Listing listing;

    @Column(nullable = false)
    private Integer quantity;

    // price frozen at checkout — NOT read back from the listing
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * The list price it was bought against, frozen alongside {@code unitPrice} so the
     * receipt can still show the saving after the sale ends. Null on orders placed
     * before this column existed.
     */
    @Column(name = "unit_full_price", precision = 12, scale = 2)
    private BigDecimal unitFullPrice;
}