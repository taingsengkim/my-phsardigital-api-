package co.istad.projectpracticum.phsardigital.features.pos.dto;

import co.istad.projectpracticum.phsardigital.features.payments.dto.PaymentResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.PaymentMethod;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseResponse;

import java.math.BigDecimal;

/**
 * @param sale          the sale itself. Note the nesting: this is not a
 *                      {@code PurchaseResponse} with two extra fields, because tendered
 *                      cash and change belong to the moment of paying rather than to the
 *                      order, which is read back later by people who never saw the till.
 * @param paymentMethod how it was paid, echoed so a receipt can say so
 * @param changeDue     null when no cash amount was given, so the till can tell "no
 *                      change" apart from "change not calculated"
 * @param payment       the QR to show the customer, on a KHQR sale only. The sale stays
 *                      {@code PENDING} until {@code POST /api/v1/payments/{uuid}/verify}
 *                      confirms the transfer with Bakong; poll it, and only then is the
 *                      sale complete. Null on a cash sale, which is done when rung up.
 */
public record PosSaleResponse(
        PurchaseResponse sale,
        PaymentMethod paymentMethod,
        BigDecimal amountTendered,
        BigDecimal changeDue,
        PaymentResponse payment
) {
}
