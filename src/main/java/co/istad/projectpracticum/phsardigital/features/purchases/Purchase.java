package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchases")
@Getter
@Setter
public class Purchase extends BasedEntity implements Persistable<UUID> {

    @Id
    private UUID uuid;

    /**
     * Checkout uses the order UUID as its idempotency key. Spring Data normally
     * treats any entity with an assigned id as existing and calls {@code merge}; this
     * flag keeps a newly constructed order on the safer {@code persist} path.
     */
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newEntity = true;

    /** Null on a counter sale: a walk-in customer has no account. */
    @Column(name = "buyer_id")
    private String buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseChannel channel = PurchaseChannel.ONLINE;

    // one order == one shop
    @ManyToOne
    @JoinColumn(name = "seller_profile_id", nullable = false)
    private SellerProfile sellerProfile;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.PENDING;

    /**
     * How the sale was paid for.
     *
     * <p>Set at the counter, where the seller knows because they watched it happen. Null
     * on an online order, which is settled in cash on delivery by a courier this system
     * never hears from — recording {@code CASH} there would be asserting something
     * nobody has confirmed. Also null on every sale rung up before this was recorded.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    /**
     * When the order reached each state, for the timeline a seller sees on an order.
     *
     * <p>{@code lastModifiedAt} cannot answer this: it moves on every write, so once an
     * order is completed the moment it was <em>accepted</em> is gone. Three nullable
     * columns rather than a history table because the state machine has four states and
     * no branches — a table would buy flexibility nothing currently needs.
     *
     * <p>Null means the order never reached that state, which is also true of every
     * order placed before these existed. A client reads them as "unknown", not "now".
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

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

    /**
     * Where the delivery point actually is, copied off the saved address at checkout on
     * the same terms as {@link #shippingAddress}: the buyer may move the pin or delete
     * the address later, and where a past order was sent must not move with it.
     *
     * <p>Null in two ordinary cases, so a client must handle its absence rather than
     * assume a pin: an order placed against a one-off typed address, which never had
     * coordinates, and any order placed before this was recorded. The precision matches
     * {@code Address}, which is where the numbers come from.
     */
    @Column(name = "delivery_latitude", precision = 10, scale = 8)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 11, scale = 8)
    private BigDecimal deliveryLongitude;

    @Column(columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    /**
     * Landmark shots of the delivery point, copied from the saved address at checkout
     * on the same terms as {@code shippingAddress}.
     */
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PurchaseDeliveryPhoto> deliveryPhotos = new ArrayList<>();

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    /**
     * Checkout assigns the cart's UUID, but the id is no longer database-generated, so
     * anything else that builds an order still gets a key instead of a null-PK failure.
     */
    @PrePersist
    private void ensureId() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        newEntity = false;
    }
}
