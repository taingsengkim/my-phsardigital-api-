package co.istad.projectpracticum.phsardigital.features.cart;
import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@Table(name = "carts", uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_id", "seller_profile_uuid"}))
@Getter
@Setter
public class Cart extends BasedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    // Keycloak sub of the buyer
    @Column(name = "buyer_id", nullable = false)
    private String buyerId;
    // one cart == one shop (per-vendor cart model)
    @ManyToOne
    @JoinColumn(name = "seller_profile_uuid", nullable = false)
    private SellerProfile sellerProfile;
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();
}