package co.istad.projectpracticum.phsardigital.features.cart;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(columnNames = {"cart_uuid", "listing_uuid"}))
@Getter
@Setter
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    @ManyToOne
    @JoinColumn(name = "cart_uuid", nullable = false)
    private Cart cart;
    @ManyToOne
    @JoinColumn(name = "listing_uuid", nullable = false)
    private Listing listing;
    @Column(nullable = false)
    private Integer quantity;
}