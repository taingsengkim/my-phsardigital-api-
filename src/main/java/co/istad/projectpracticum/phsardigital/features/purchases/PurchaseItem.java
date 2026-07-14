package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    @Column(nullable = false)
    private Double unitPrice;
}