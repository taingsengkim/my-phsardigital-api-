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

    /**
     * What the unit cost the shop, frozen at the moment of sale alongside the two prices
     * above.
     *
     * <p>Snapshotted for the same reason they are, and it matters more here: restocking
     * at a new price would otherwise rewrite the profit on every sale already made, and
     * a shop's history of what it earned would change every time it bought more stock.
     *
     * <p>Null when the listing had no cost recorded, and on every sale made before this
     * existed — so a profit figure has to be reported over the lines that have it rather
     * than assuming zero, which would read as pure profit.
     */
    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;
}