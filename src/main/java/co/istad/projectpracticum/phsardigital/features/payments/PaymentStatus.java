package co.istad.projectpracticum.phsardigital.features.payments;

public enum PaymentStatus {
    /** QR issued, nobody has paid it yet. */
    PENDING,
    /** Bakong confirmed the transfer and whatever it bought has been granted. */
    PAID,
    /**
     * The QR outlived its validity without a matching transfer. Checked against Bakong
     * before it is written off, and still settleable afterwards — see
     * {@code PaymentServiceImpl#verify}.
     */
    EXPIRED
}
