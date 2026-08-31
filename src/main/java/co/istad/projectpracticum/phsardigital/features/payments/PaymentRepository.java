package co.istad.projectpracticum.phsardigital.features.payments;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Serializes confirmation of one payment.
     *
     * <p>Two clients polling the same QR at once would otherwise both read it as
     * {@code PENDING}, both hear "paid" from Bakong, and both grant the subscription —
     * giving away two periods for one payment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.uuid = :uuid")
    Optional<Payment> findByUuidForUpdate(@Param("uuid") UUID uuid);

    /**
     * The seller's live attempt at buying this exact thing, if there is one.
     *
     * <p>Backs the idempotency of starting a payment: pressing Subscribe twice should
     * return the same QR, not mint a second one and leave the seller looking at two
     * codes with no way to tell which is real.
     */
    Optional<Payment> findFirstByPayerIdAndPurposeAndReferenceAndStatusOrderByCreatedAtDesc(
            String payerId, PaymentPurpose purpose, String reference, PaymentStatus status);

    Page<Payment> findByPayerIdOrderByCreatedAtDesc(String payerId, Pageable pageable);

    /**
     * Pending payments whose QR lapsed before the given moment, oldest first.
     *
     * <p>Read rather than bulk-updated, because each one is checked against Bakong
     * before it is written off — see {@code PaymentExpirySweeper} for why a payment is
     * never expired on the strength of the clock alone.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.expiresAt < :cutoff "
            + "ORDER BY p.expiresAt ASC")
    List<Payment> findLapsed(@Param("status") PaymentStatus status,
                             @Param("cutoff") LocalDateTime cutoff,
                             Pageable pageable);
}
