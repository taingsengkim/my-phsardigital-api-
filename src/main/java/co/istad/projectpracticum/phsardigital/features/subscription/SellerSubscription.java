package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A seller's current subscription — one row per seller, keyed by the same Keycloak
 * subject as {@code SellerProfile}, so checking whether somebody may post is a
 * single primary-key lookup on the hot path.
 *
 * <p>Renewing overwrites the row rather than appending to it: there is no billing
 * yet, so there is no payment history worth keeping. A real provider integration
 * would add a separate ledger table beside this one.
 */
@Entity
@Table(name = "seller_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class SellerSubscription extends BasedEntity {

    public SellerSubscription(String sellerId) {
        this.sellerId = sellerId;
    }

    @Id
    @Column(name = "seller_id")
    private String sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Whether the subscription is currently good, judged against the clock rather
     * than against {@link #status} alone. Nothing sweeps the table on a schedule, so
     * a row can sit at {@code ACTIVE} well past its expiry — reading the stored flag
     * on its own would hand a lapsed seller a working subscription.
     */
    public boolean isCurrentlyActive() {
        return status == SubscriptionStatus.ACTIVE
                && expiresAt != null
                && expiresAt.isAfter(LocalDateTime.now());
    }
}
