package co.istad.projectpracticum.phsardigital.features.stock;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One append-only change to a listing's stock. {@code listings.stock_qty} is the running
 * total these add up to; this table is what explains it — which channel took the goods,
 * against which order, and who did it. Rows are never updated or deleted.
 */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_listing", columnList = "listing_uuid, created_at"),
        @Index(name = "idx_stock_movements_ref", columnList = "ref_uuid")
})
@Getter
@Setter
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_uuid", nullable = false)
    private Listing listing;

    /** Signed: negative took goods off the shelf, positive put them back. */
    @Column(nullable = false)
    private Integer delta;

    /** The running total after this movement, so history reads without re-summing. */
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMovementReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockChannel channel;

    /** The order or counter sale this movement belongs to, when there is one. */
    @Column(name = "ref_uuid")
    private UUID refUuid;

    /** Keycloak subject that caused it; null for anything the system did on its own. */
    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
