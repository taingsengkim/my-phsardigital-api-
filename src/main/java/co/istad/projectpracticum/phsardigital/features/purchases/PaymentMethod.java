package co.istad.projectpracticum.phsardigital.features.purchases;

/**
 * How a sale was paid for.
 *
 * <p>Recorded rather than inferred, because it cannot be recovered afterwards: two sales
 * of the same basket for the same total look identical once the customer has left, and a
 * shop reconciling its till at closing needs to know which of them put cash in the drawer.
 */
public enum PaymentMethod {

    /** Notes and coins at the counter, or collected by the courier on delivery. */
    CASH,

    /**
     * Scanned and paid through Bakong.
     *
     * <p>At the counter the money moves directly between the customer's bank and the
     * shop's own — this platform is not in the middle of it and cannot confirm it, so
     * the value records what the seller says happened rather than something verified.
     * That is different from a subscription payment, which is collected by the platform
     * and confirmed against Bakong before anything is granted.
     */
    KHQR
}
