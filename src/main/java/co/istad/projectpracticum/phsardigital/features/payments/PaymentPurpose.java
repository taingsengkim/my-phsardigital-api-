package co.istad.projectpracticum.phsardigital.features.payments;

/**
 * What a payment buys.
 *
 * <p>Each value needs a {@link PaymentSettlement} to grant the thing once the money
 * arrives; {@code PaymentServiceImpl} refuses to start a payment for a purpose nothing
 * handles, so adding a value here without its handler fails loudly rather than taking
 * money and delivering nothing.
 */
public enum PaymentPurpose {

    /** A seller buying a period on a subscription plan. The reference is the plan code. */
    SUBSCRIPTION,

    /**
     * A walk-in customer paying for a counter sale by QR. The reference is the sale's
     * UUID, and the money is collected by the shop's own Bakong account rather than the
     * marketplace's — see {@code Payment#collectingAccountId}.
     */
    POS_SALE
}
