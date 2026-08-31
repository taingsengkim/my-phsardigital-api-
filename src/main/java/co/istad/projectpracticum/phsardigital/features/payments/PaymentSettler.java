package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongProps;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Writes down that a payment settled, and grants what it bought.
 *
 * <p>Separate from {@code PaymentServiceImpl} for one reason: the transaction boundary.
 * Confirming a payment means asking Bakong, which is a network call that can take
 * seconds, and then taking a row lock. Doing both in one transaction would hold a
 * database lock across the network, so the service calls Bakong first and hands the
 * answer here — and Spring only applies {@code @Transactional} across beans, not to a
 * method calling its own neighbour.
 *
 * <p>The lock is retaken and the status re-read inside {@link #settle}, so an answer
 * that went stale while it was in flight cannot grant a second subscription.
 */
@Component
@Slf4j
class PaymentSettler {

    private final PaymentRepository paymentRepository;
    private final BakongProps props;
    private final Map<PaymentPurpose, PaymentSettlement> settlements =
            new EnumMap<>(PaymentPurpose.class);

    PaymentSettler(PaymentRepository paymentRepository,
                   BakongProps props,
                   List<PaymentSettlement> settlements) {
        this.paymentRepository = paymentRepository;
        this.props = props;
        for (PaymentSettlement settlement : settlements) {
            PaymentSettlement clash = this.settlements.put(settlement.purpose(), settlement);
            if (clash != null) {
                // Two handlers for one purpose means one of them silently never runs,
                // and which one is down to bean ordering. Refuse to start instead.
                throw new IllegalStateException("Two PaymentSettlement beans claim "
                        + settlement.purpose() + ": " + clash.getClass().getName()
                        + " and " + settlement.getClass().getName());
            }
        }
    }

    /** Whether anything can grant this purpose; checked before a QR is ever issued. */
    boolean handles(PaymentPurpose purpose) {
        return settlements.containsKey(purpose);
    }

    /**
     * Marks the payment paid and grants what it bought.
     *
     * <p>Deliberately settles an {@code EXPIRED} payment too. Expiry is our judgement
     * that a QR had gone quiet; if Bakong later says the transfer happened, then it
     * happened, and the seller is owed their plan regardless of what this table decided
     * in the meantime. Refusing there would be keeping money for nothing.
     *
     * @return the settled payment, or the untouched one if it was already settled
     */
    @Transactional
    Payment settle(UUID uuid, BakongTransaction transaction) {
        Payment payment = paymentRepository.findByUuidForUpdate(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment not found."));

        // Another poll got here first while our answer was in flight from Bakong.
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment;
        }

        requireTransactionMatches(payment, transaction);

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setBakongHash(transaction.hash());
        payment.setFromAccountId(transaction.fromAccountId());

        PaymentSettlement settlement = settlements.get(payment.getPurpose());
        if (settlement == null) {
            // start() refuses unhandled purposes, so reaching this means a handler was
            // removed while a payment for it was open. The money has moved and nothing
            // can grant what it bought, which is worth failing loudly over.
            log.error("Payment {} settled for purpose {} but nothing handles it; "
                    + "the payer has been charged and granted nothing.",
                    payment.getUuid(), payment.getPurpose());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "This payment cannot be applied. Contact support quoting "
                            + payment.getUuid() + ".");
        }
        settlement.settle(payment);

        log.info("Payment {} of {} {} settled for {} ({} {}) from {}",
                payment.getUuid(), payment.getAmount(), payment.getCurrency(),
                payment.getPayerId(), payment.getPurpose(), payment.getReference(),
                transaction.fromAccountId());
        return paymentRepository.save(payment);
    }

    /** Records that a QR lapsed unpaid. Only ever called after Bakong has been asked. */
    @Transactional
    void expire(UUID uuid) {
        paymentRepository.findByUuidForUpdate(uuid).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.EXPIRED);
                paymentRepository.save(payment);
            }
        });
    }

    /**
     * Checks that the transfer Bakong described is the one we asked for.
     *
     * <p>Strictly this is belt and braces: the MD5 we look up covers the entire QR
     * payload, collecting account and amount included, so a matching transaction can
     * hardly be for the wrong sum. That is exactly why a mismatch here is treated as
     * alarming rather than as an ordinary rejection — it would mean the hash no longer
     * means what the whole design assumes it means, and granting a plan on the strength
     * of it would be unwise.
     */
    private void requireTransactionMatches(Payment payment, BakongTransaction transaction) {
        String expectedAccount = props.getAccountId();
        if (transaction.toAccountId() != null
                && !transaction.toAccountId().equalsIgnoreCase(expectedAccount)) {
            log.error("Payment {} matched a transfer to {} but this server collects to {}",
                    payment.getUuid(), transaction.toAccountId(), expectedAccount);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "This payment could not be verified. Contact support quoting "
                            + payment.getUuid() + ".");
        }

        BigDecimalMismatch mismatch = amountMismatch(payment, transaction);
        if (mismatch != null) {
            log.error("Payment {} expected {} {} but Bakong reported {} {}",
                    payment.getUuid(), mismatch.expectedAmount(), mismatch.expectedCurrency(),
                    mismatch.actualAmount(), mismatch.actualCurrency());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "This payment could not be verified. Contact support quoting "
                            + payment.getUuid() + ".");
        }
    }

    /**
     * @return the mismatch, or null when the transfer agrees with the payment. Fields
     *         Bakong omitted are not treated as disagreement — an absent amount is a
     *         gap in their response, not evidence the wrong sum was sent.
     */
    private static BigDecimalMismatch amountMismatch(Payment payment, BakongTransaction transaction) {
        boolean amountDiffers = transaction.amount() != null
                && Money.of(transaction.amount()).compareTo(payment.getAmount()) != 0;
        boolean currencyDiffers = transaction.currency() != null
                && !transaction.currency().trim().toUpperCase(Locale.ROOT)
                .equals(payment.getCurrency().name());
        if (!amountDiffers && !currencyDiffers) {
            return null;
        }
        return new BigDecimalMismatch(payment.getAmount(), payment.getCurrency().name(),
                transaction.amount(), transaction.currency());
    }

    private record BigDecimalMismatch(java.math.BigDecimal expectedAmount,
                                      String expectedCurrency,
                                      java.math.BigDecimal actualAmount,
                                      String actualCurrency) {
    }
}
