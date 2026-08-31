package co.istad.projectpracticum.phsardigital.features.payments;

/**
 * Grants whatever a payment bought, once the money is confirmed.
 *
 * <p>This is the seam that keeps the dependency pointing one way. Payments knows how to
 * collect money and nothing about subscriptions; the subscription feature implements
 * this and knows nothing about KHQR. Without it the two would have to import each
 * other, since collecting starts in subscriptions and finishes in payments.
 *
 * <p>Implementations are called from inside the transaction that marks the payment
 * {@code PAID}, while its row is locked, and are called at most once per payment. They
 * may throw: the payment is then left unsettled and rolled back with it, so a seller
 * whose plan could not be activated is not recorded as having bought it.
 */
public interface PaymentSettlement {

    /** Which payments this handles. Exactly one implementation per purpose. */
    PaymentPurpose purpose();

    /**
     * Grants the thing. Must be idempotent in spirit even though the caller guarantees
     * a single invocation, because retries after a failed transaction land here again.
     */
    void settle(Payment payment);

    /**
     * Undoes whatever was held while the payment was open, once it is written off.
     *
     * <p>Called only after Bakong has confirmed no such transfer exists — the sweeper
     * never expires a payment on the clock alone. Nothing to do for a purpose that holds
     * nothing, which is why it defaults to doing nothing; a counter sale, which has
     * already taken the goods out of stock, gives them back here.
     */
    default void onExpired(Payment payment) {
        // Most purposes reserve nothing while they wait.
    }
}
