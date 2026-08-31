package co.istad.projectpracticum.phsardigital.features.payments;

/**
 * The two currencies KHQR carries.
 *
 * <p>Riel is quoted in whole units and dollars to two decimal places; the difference is
 * enforced when a QR is minted, since the payload has no room to round afterwards.
 */
public enum PaymentCurrency {
    USD,
    KHR
}
