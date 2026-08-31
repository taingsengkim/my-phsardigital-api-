package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.features.payments.dto.PaymentResponse;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Collects money over Bakong KHQR.
 *
 * <p>Bakong publishes no webhook, so nothing tells this API that a QR was paid: the
 * answer only exists when somebody asks for it. Confirmation therefore has two routes,
 * and both end in the same place. {@link #verify} is the fast one, driven by the client
 * polling while the payer has the QR on screen; {@code PaymentExpirySweeper} is the
 * safety net, which asks once more before any payment is written off so that a transfer
 * made by somebody who then closed their browser is not lost.
 */
public interface PaymentService {

    /**
     * Issues a QR for something the caller is buying, or returns the one they already
     * have open for it.
     *
     * <p>Reusing a live payment is what makes pressing Subscribe twice harmless. A
     * fresh QR each time would leave a seller holding two codes, only one of which the
     * server is watching, with no way to tell them apart.
     *
     * @param purpose   what is being bought; must have a {@link PaymentSettlement}
     * @param reference what specifically, in that purpose's own terms
     * @param payerId   the Keycloak subject who will pay and who alone may poll it
     * @param amount    positive, and already at the currency's scale
     */
    Payment start(PaymentPurpose purpose, String reference, String payerId,
                  BigDecimal amount, PaymentCurrency currency);

    /**
     * Issues a QR drawn on somebody else's Bakong account — a shop collecting at its own
     * counter rather than the marketplace collecting a subscription.
     *
     * <p>The money never passes through this platform. Settlement checks the transfer
     * landed in {@code accountId} specifically, recorded on the payment so it cannot
     * drift after the QR was issued.
     *
     * @param accountId   the collecting account, {@code name@bank}
     * @param accountName what the payer sees when they scan
     * @param city        the shop's city, for the same block of the payload
     */
    Payment startForAccount(PaymentPurpose purpose, String reference, String payerId,
                            BigDecimal amount, PaymentCurrency currency,
                            String accountId, String accountName, String city);

    /**
     * Asks Bakong whether this payment arrived, and grants what it bought if so.
     *
     * <p>Safe to call repeatedly — that is the intended use, since it is how a client
     * discovers that a payment landed. Settling happens at most once however many
     * callers race.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when the
     *         payment is not the caller's, 502/503/504 when Bakong could not be asked —
     *         never a quiet "unpaid", which would be indistinguishable from a real one
     */
    PaymentResponse verify(UUID uuid);

    /** One of the caller's own payments, without asking Bakong anything. */
    PaymentResponse findMine(UUID uuid);

    /** The caller's payment history, newest first. */
    Page<PaymentResponse> findMine(int pageNumber, int pageSize);
}
