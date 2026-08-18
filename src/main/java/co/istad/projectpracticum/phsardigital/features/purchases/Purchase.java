package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchases")
@Getter
@Setter
public class Purchase extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    // one order == one shop
    @ManyToOne
    @JoinColumn(name = "seller_profile_id", nullable = false)
    private SellerProfile sellerProfile;

    @Column(nullable = false)
    private Double totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.PENDING;

    /**
     * Delivery details copied at checkout, never a reference to the saved address they
     * came from. The same reasoning as {@code PurchaseItem.unitPrice}: editing or
     * deleting an address later must not rewrite where a past order was sent.
     */
    @Column(columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", length = 30)
    private String recipientPhone;

    @Column(columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();
}