package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.payments.dto.PaymentResponse;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongClient;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongTransaction;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.KhqrGenerator;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.KhqrPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentSettler paymentSettler;
    private final KhqrGenerator khqrGenerator;
    private final BakongClient bakongClient;
    private final co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongProps props;

    @Override
    @Transactional
    public Payment startForAccount(PaymentPurpose purpose, String reference, String payerId,
                                   BigDecimal amount, PaymentCurrency currency,
                                   String accountId, String accountName, String city) {
        Payment payment = open(purpose, reference, payerId, amount, currency, accountId);
        if (payment.getMd5() != null) {
            return payment;
        }
        KhqrPayload payload = khqrGenerator.generateForAccount(
                payment.getUuid(), accountId, accountName, city, payment.getAmount(), currency);
        return persist(payment, payload, purpose, reference, payerId);
    }

    @Override
    @Transactional
    public Payment start(PaymentPurpose purpose, String reference, String payerId,
                         BigDecimal amount, PaymentCurrency currency) {
        Payment payment = open(purpose, reference, payerId, amount, currency, props.getAccountId());
        if (payment.getMd5() != null) {
            return payment;
        }
        // Generated against the payment's own UUID, which is what makes the resulting
        // MD5 unique even for two identical purchases; see KhqrGenerator#billNumberFor.
        KhqrPayload payload = khqrGenerator.generate(
                payment.getUuid(), payment.getAmount(), currency);
        return persist(payment, payload, purpose, reference, payerId);
    }

    /**
     * Finds the caller's live payment for this exact thing, or prepares a fresh one.
     *
     * <p>A returned payment that already carries an MD5 is the existing one and needs no
     * QR minting; one without has never been saved. Splitting it this way keeps the
     * reuse rule in a single place while letting each caller mint on the account it
     * collects into.
     */
    private Payment open(PaymentPurpose purpose, String reference, String payerId,
                         BigDecimal amount, PaymentCurrency currency, String collectingAccountId) {
        if (!paymentSettler.handles(purpose)) {
            // Taking money for something nothing can grant is the one failure mode here
            // that cannot be undone by retrying, so it is checked before a QR exists.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Nothing is configured to fulfil a " + purpose + " payment.");
        }
        if (collectingAccountId == null || collectingAccountId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No Bakong account is configured to collect this payment.");
        }

        Payment existing = paymentRepository
                .findFirstByPayerIdAndPurposeAndReferenceAndStatusOrderByCreatedAtDesc(
                        payerId, purpose, reference, PaymentStatus.PENDING)
                .orElse(null);
        // Only a QR that is still scannable is worth handing back. One that lapsed
        // between the two presses is not reusable: its expiry is baked into the payload,
        // so no amount of goodwill here would make a banking app accept it.
        if (existing != null && existing.isPayable()
                && existing.getAmount().compareTo(amount) == 0
                && existing.getCurrency() == currency
                && collectingAccountId.equalsIgnoreCase(existing.getCollectingAccountId())) {
            return existing;
        }

        Payment payment = new Payment();
        payment.setUuid(UUID.randomUUID());
        payment.setPurpose(purpose);
        payment.setReference(reference);
        payment.setPayerId(payerId);
        payment.setAmount(Money.of(amount));
        payment.setCurrency(currency);
        payment.setCollectingAccountId(collectingAccountId);
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private Payment persist(Payment payment, KhqrPayload payload, PaymentPurpose purpose,
                            String reference, String payerId) {
        payment.setQr(payload.qr());
        payment.setMd5(payload.md5());
        payment.setExpiresAt(payload.expiresAt());

        Payment saved = paymentRepository.save(payment);
        log.info("Payment {} opened: {} {} from {} for {} ({}) into {}", saved.getUuid(),
                saved.getAmount(), saved.getCurrency(), payerId, purpose, reference,
                saved.getCollectingAccountId());
        return saved;
    }

    /**
     * Deliberately not {@code @Transactional}.
     *
     * <p>The Bakong call in the middle can take seconds, and wrapping the whole method
     * would hold the payment's row lock across it. Instead the state is read, Bakong is
     * asked outside any transaction, and only then does {@link PaymentSettler} take the
     * lock — where it re-reads the status, so an answer that went stale in flight
     * cannot grant anything twice.
     */
    @Override
    public PaymentResponse verify(UUID uuid) {
        String payerId = AuthUtils.extractUserId();
        Payment payment = requireOwned(uuid, payerId);

        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentResponse.of(payment);
        }

        BakongTransaction transaction = bakongClient.checkByMd5(payment.getMd5());
        if (!transaction.settled()) {
            // Nobody has paid yet. An expired QR is left as it is rather than reported
            // as a failure: the sweeper decides that, after asking Bakong itself.
            return PaymentResponse.of(payment);
        }
        return PaymentResponse.of(paymentSettler.settle(uuid, transaction));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findMine(UUID uuid) {
        return PaymentResponse.of(requireOwned(uuid, AuthUtils.extractUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> findMine(int pageNumber, int pageSize) {
        return paymentRepository
                .findByPayerIdOrderByCreatedAtDesc(
                        AuthUtils.extractUserId(), PageRequest.of(pageNumber, pageSize))
                .map(PaymentResponse::of);
    }

    /**
     * A payment belonging to somebody else answers 404 rather than 403: whether a given
     * UUID names a real payment is not something an unrelated caller should be able to
     * find out.
     */
    private Payment requireOwned(UUID uuid, String payerId) {
        Payment payment = paymentRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment not found."));
        if (!payment.getPayerId().equals(payerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found.");
        }
        return payment;
    }
}
