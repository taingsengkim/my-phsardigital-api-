package co.istad.projectpracticum.phsardigital.features.payments.dto;

import co.istad.projectpracticum.phsardigital.features.payments.Payment;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentCurrency;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentPurpose;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A payment as the paying seller sees it.
 *
 * @param qr        the KHQR payload to render as a QR code. Still returned once the
 *                  payment is settled or lapsed, because the payload carries its own
 *                  expiry and banking apps refuse it on their own — there is nothing to
 *                  protect by withholding it, and clients redraw the same screen.
 * @param md5       the payload's hash. Exposed because it is what appears in the
 *                  merchant's Bakong statement, so it is the reference a seller and an
 *                  admin can compare when a payment is disputed.
 * @param payable   whether this is still worth showing. False once paid or lapsed.
 */
public record PaymentResponse(
        UUID uuid,
        PaymentPurpose purpose,
        String reference,
        BigDecimal amount,
        PaymentCurrency currency,
        PaymentStatus status,
        boolean payable,
        String qr,
        String md5,
        LocalDateTime expiresAt,
        LocalDateTime paidAt
) {

    public static PaymentResponse of(Payment payment) {
        return new PaymentResponse(
                payment.getUuid(),
                payment.getPurpose(),
                payment.getReference(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.isPayable(),
                payment.getQr(),
                payment.getMd5(),
                payment.getExpiresAt(),
                payment.getPaidAt());
    }
}
