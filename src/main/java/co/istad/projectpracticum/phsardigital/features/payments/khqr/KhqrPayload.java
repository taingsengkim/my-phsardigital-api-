package co.istad.projectpracticum.phsardigital.features.payments.khqr;

import java.time.LocalDateTime;

/**
 * A minted KHQR: the string a payer scans, and the handle we ask Bakong about later.
 *
 * @param qr        the EMVCo payload, rendered as a QR code by the client
 * @param md5       MD5 of {@link #qr}. This is the only identifier Bakong will accept
 *                  when asked whether the payment arrived, and because the hash covers
 *                  the whole payload — collecting account and amount included — a
 *                  transaction that matches it is necessarily a transfer of the right
 *                  amount to the right account.
 * @param expiresAt when the payload stops being payable; encoded inside {@link #qr},
 *                  so banking apps enforce it too
 */
public record KhqrPayload(String qr, String md5, LocalDateTime expiresAt) {
}
