package co.istad.projectpracticum.phsardigital.features.payments.khqr;

import java.math.BigDecimal;

/**
 * What Bakong says about one transaction.
 *
 * <p>{@link #settled()} false means "no such transaction yet", which is the ordinary
 * answer while somebody is still looking at the QR. It never means "the question could
 * not be asked" — {@link BakongClient} raises those as exceptions, so a network fault
 * or an expired token can never be mistaken here for an unpaid invoice.
 *
 * @param hash          Bakong's own identifier for the transfer, kept for reconciliation
 * @param fromAccountId who paid
 * @param toAccountId   who was paid; checked against our configured merchant account
 * @param amount        what moved, checked against what was asked for
 * @param currency      as reported by Bakong, e.g. {@code USD}
 */
public record BakongTransaction(boolean settled,
                                String hash,
                                String fromAccountId,
                                String toAccountId,
                                BigDecimal amount,
                                String currency) {

    /** The answer while nobody has paid yet. */
    public static BakongTransaction unsettled() {
        return new BakongTransaction(false, null, null, null, null, null);
    }
}
