package co.istad.projectpracticum.phsardigital.features.payments.khqr;

import co.istad.projectpracticum.phsardigital.features.payments.PaymentCurrency;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.IndividualInfo;
import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import kh.gov.nbc.bakong_khqr.model.KHQRStatus;
import kh.gov.nbc.bakong_khqr.model.MerchantInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds the KHQR payload a seller scans to pay us.
 *
 * <p>The payload itself is assembled by the National Bank of Cambodia's own SDK. That
 * is the whole reason the dependency is here: the tag layout, the field lengths and
 * the CRC are NBC's specification, and a QR that gets any of them subtly wrong is not
 * rejected by us — it is rejected by whichever banking app the seller happens to open,
 * which is a terrible place to discover the mistake. This class only supplies the
 * merchant identity, the amount and an expiry, then translates the SDK's error channel
 * into the exceptions the rest of the API speaks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KhqrGenerator {

    /** {@code Constant.SUCCESS_CODE} in the SDK; anything else carries an error code. */
    private static final int SDK_SUCCESS = 0;

    /** KHQR's own ceilings; see {@code KHQRValidation} in the NBC SDK. */
    private static final int MAX_MERCHANT_NAME = 25;
    private static final int MAX_MERCHANT_CITY = 15;

    private final BakongProps props;

    /**
     * Mints a QR for one payment.
     *
     * <p>The amount is non-null and positive by contract, which makes this a
     * <em>dynamic</em> KHQR — the kind that names a sum rather than inviting the payer
     * to type one. Dynamic codes must carry an expiry, and the SDK refuses to build one
     * without it, so {@link BakongProps#getQrValidity()} is always applied.
     *
     * @param paymentUuid identifies the payment; becomes the bill number, which is what
     *                    keeps two payments for the same plan at the same price from
     *                    hashing to the same MD5 and being mistaken for one another
     */
    public KhqrPayload generate(UUID paymentUuid, BigDecimal amount, PaymentCurrency currency) {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payment is not configured on this server.");
        }
        requirePayableAmount(amount, currency);
        Instant expiry = Instant.now().plus(props.getQrValidity());

        MerchantInfo merchant = new MerchantInfo();
        merchant.setBakongAccountId(props.getAccountId());
        merchant.setMerchantId(props.getMerchantId());
        merchant.setAcquiringBank(props.getAcquiringBank());
        merchant.setMerchantName(props.getMerchantName());
        merchant.setMerchantCity(props.getMerchantCity());
        merchant.setCurrency(toSdkCurrency(currency));
        merchant.setAmount(amount.doubleValue());
        merchant.setBillNumber(billNumberFor(paymentUuid));
        merchant.setMobileNumber(props.getMobileNumber());
        merchant.setStoreLabel(props.getStoreLabel());
        merchant.setTerminalLabel(props.getTerminalLabel());
        merchant.setExpirationTimestamp(expiry.toEpochMilli());

        return toPayload(BakongKHQR.generateMerchant(merchant), paymentUuid, expiry);
    }

    /**
     * Mints a QR drawn on somebody else's Bakong account — a shop collecting at its own
     * counter.
     *
     * <p>An individual code rather than a merchant one, because a merchant code requires
     * a merchant id and acquiring bank issued by a bank, and the shops using this have an
     * ordinary Bakong account and nothing more. The money moves straight from the
     * customer to {@code accountId}; the marketplace is not in the path and never holds
     * it.
     *
     * @param accountId   the collecting account, {@code name@bank}
     * @param accountName what the payer sees in their banking app, at most 25 characters
     * @param city        at most 15 characters
     */
    public KhqrPayload generateForAccount(UUID paymentUuid, String accountId, String accountName,
                                          String city, BigDecimal amount, PaymentCurrency currency) {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payment is not configured on this server.");
        }
        requirePayableAmount(amount, currency);
        Instant expiry = Instant.now().plus(props.getQrValidity());

        IndividualInfo individual = new IndividualInfo();
        individual.setBakongAccountId(accountId);
        individual.setMerchantName(trimTo(accountName, MAX_MERCHANT_NAME));
        individual.setMerchantCity(trimTo(city, MAX_MERCHANT_CITY));
        individual.setCurrency(toSdkCurrency(currency));
        individual.setAmount(amount.doubleValue());
        individual.setBillNumber(billNumberFor(paymentUuid));
        individual.setTerminalLabel(props.getTerminalLabel());
        individual.setExpirationTimestamp(expiry.toEpochMilli());

        return toPayload(BakongKHQR.generateIndividual(individual), paymentUuid, expiry);
    }

    private KhqrPayload toPayload(KHQRResponse<KHQRData> response, UUID paymentUuid, Instant expiry) {
        KHQRStatus status = response.getKHQRStatus();
        if (status != null && status.getCode() != SDK_SUCCESS) {
            log.error("KHQR generation failed for payment {}: code={} errorCode={} message={}",
                    paymentUuid, status.getCode(), status.getErrorCode(), status.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not generate a payment QR: " + status.getMessage());
        }
        KHQRData data = response.getData();
        return new KhqrPayload(data.getQr(), data.getMd5(),
                LocalDateTime.ofInstant(expiry, ZoneId.systemDefault()));
    }

    /**
     * KHQR caps the name and city, and a shop's registered business name is routinely
     * longer than 25 characters. Truncating is better than refusing the sale over it —
     * the account id is what the money follows, and the name is only what the payer
     * reads on the confirmation screen.
     */
    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max).trim();
    }

    /**
     * KHQR takes a bill number of at most 25 characters, and we need one that is unique
     * per payment: the MD5 Bakong is later asked about covers the entire payload, so two
     * payments identical in every visible respect — same plan, same price, same merchant
     * — would otherwise share a hash, and settling one would settle the other.
     *
     * <p>Base 36 over the UUID's 128 bits needs 25 characters at the very most, which
     * is exactly the ceiling, and yields only digits and letters.
     */
    static String billNumberFor(UUID uuid) {
        ByteBuffer bytes = ByteBuffer.allocate(Long.BYTES * 2);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return new BigInteger(1, bytes.array()).toString(36).toUpperCase(Locale.ROOT);
    }

    /**
     * KHQR carries riel as whole numbers and dollars to at most two decimal places.
     * Both are checked before the SDK sees them so the failure names the amount.
     */
    private static void requirePayableAmount(BigDecimal amount, PaymentCurrency currency) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "A payment QR needs a positive amount.");
        }
        BigDecimal trimmed = amount.stripTrailingZeros();
        int decimals = Math.max(trimmed.scale(), 0);
        int allowed = currency == PaymentCurrency.KHR ? 0 : 2;
        if (decimals > allowed) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "KHQR cannot carry " + amount + " " + currency + ": at most "
                            + allowed + " decimal place(s) are allowed.");
        }
    }

    private static KHQRCurrency toSdkCurrency(PaymentCurrency currency) {
        return currency == PaymentCurrency.KHR ? KHQRCurrency.KHR : KHQRCurrency.USD;
    }
}
