package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One attempt to collect money through KHQR, and the record of what came of it.
 *
 * <p>This is a ledger: rows are never rewritten to describe a different payment and
 * never deleted, because a row is the only local evidence that a seller was charged.
 * {@code SellerSubscription} deliberately keeps no history of its own — it is
 * overwritten on renewal — so the answer to "what did this shop actually pay for, and
 * when" lives here or nowhere.
 */
@Entity
@Table(name = "payments", indexes = {
        // The sweeper reads pending rows by age, and every confirmation is a lookup by
        // hash; neither should be a sequential scan once this table has a year in it.
        @Index(name = "idx_payments_status_expires_at", columnList = "status, expires_at"),
        @Index(name = "idx_payments_payer_purpose", columnList = "payer_id, purpose, reference")
})
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BasedEntity {

    @Id
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentPurpose purpose;

    /**
     * What is being bought, in terms the purpose's {@link PaymentSettlement} understands
     * — a subscription plan code, today. Deliberately a plain string rather than a
     * foreign key: a payment must stay readable after whatever it refers to is retired.
     */
    @Column(nullable = false, length = 100)
    private String reference;

    /** The Keycloak subject who owes the money and who alone may poll this payment. */
    @Column(name = "payer_id", nullable = false)
    private String payerId;

    /**
     * The Bakong account this payment is collected into, recorded per payment rather
     * than read from configuration.
     *
     * <p>A subscription is collected by the marketplace; a counter sale is collected by
     * the shop that made it. Settlement checks the transfer landed <em>here</em>, so the
     * account a payment was drawn on cannot drift after the QR was issued — which it
     * would if the check read today's configuration, or today's shop profile.
     */
    @Column(name = "collecting_account_id", nullable = false, length = 32)
    private String collectingAccountId;

    /**
     * Snapshotted at issue, not read back from the plan. An admin may reprice a plan
     * while somebody has its QR open, and the QR — whose hash is already fixed — commits
     * us to the old price.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PaymentCurrency currency = PaymentCurrency.USD;

    /**
     * MD5 of {@link #qr}, and the handle Bakong answers questions about.
     *
     * <p>Unique, and enforced by the database rather than by convention: two rows
     * sharing a hash would be indistinguishable to Bakong, so confirming one would
     * silently confirm the other. The bill number written into every payload is derived
     * from {@link #uuid} precisely to make that impossible, and this constraint is the
     * proof rather than the promise.
     */
    @Column(nullable = false, length = 32, unique = true)
    private String md5;

    /** The EMVCo payload the payer scans. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String qr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** When the payload stops being payable. Also encoded inside {@link #qr}. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Bakong's identifier for the settled transfer; null until then. */
    @Column(name = "bakong_hash", length = 128)
    private String bakongHash;

    /** The account the money came from, kept so a disputed payment can be traced. */
    @Column(name = "from_account_id", length = 64)
    private String fromAccountId;

    /**
     * Whether this payment is still worth showing a QR for. A payment past its expiry
     * can still be settled — see {@code PaymentServiceImpl#verify} — but it should no
     * longer be presented as something to scan.
     */
    public boolean isPayable() {
        return status == PaymentStatus.PENDING
                && expiresAt != null
                && expiresAt.isAfter(LocalDateTime.now());
    }

    @PrePersist
    private void ensureId() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
