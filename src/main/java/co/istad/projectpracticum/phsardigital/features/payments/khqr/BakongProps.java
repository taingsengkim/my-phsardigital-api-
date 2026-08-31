package co.istad.projectpracticum.phsardigital.features.payments.khqr;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * The merchant identity every KHQR this API mints is drawn on, and the credentials
 * used to ask Bakong whether one was paid.
 *
 * <p>Everything here except {@link #apiToken} is public information — it is encoded
 * into the QR itself, which is then shown to whoever is paying. The token is the only
 * secret, and it is the one field that should come from the environment.
 *
 * <p>The length ceilings enforced below are the National Bank of Cambodia's, not ours.
 * They are checked at startup rather than at the first checkout: a merchant name one
 * character too long is a deployment mistake, and it should stop the deployment
 * instead of surfacing as a failed payment for the first seller who tries to pay.
 */
@Configuration
@ConfigurationProperties(prefix = "bakong")
@Getter
@Setter
@NoArgsConstructor
public class BakongProps {

    /** KHQR caps these three; see {@code KHQRValidation} in the NBC SDK. */
    private static final int MAX_MERCHANT_NAME = 25;
    private static final int MAX_MERCHANT_CITY = 15;
    private static final int MAX_ACCOUNT_FIELD = 32;

    /**
     * Bakong Open API root. The production system is
     * {@code https://api-bakong.nbc.gov.kh}; {@code https://sit-api-bakong.nbc.gov.kh}
     * is the sandbox, and a token issued for one is not valid on the other.
     */
    private String baseUrl = "https://api-bakong.nbc.gov.kh";

    /**
     * Bearer token from the Bakong developer portal.
     *
     * <p>It is a JWT with a fixed expiry — roughly 90 days — and Bakong does not renew
     * it silently. When it lapses, every {@code check_transaction} call answers 401 and
     * {@link BakongClient} reports that plainly rather than reading it as "not paid",
     * because quietly treating an expired credential as an unpaid invoice would take
     * money from sellers and give them nothing.
     */
    private String apiToken;

    /** The Bakong account collecting the money, in {@code name@bank} form. */
    private String accountId;

    /** Shown in the payer's banking app. At most 25 characters. */
    private String merchantName;

    /** Shown beneath the name. At most 15 characters. */
    private String merchantCity = "PHNOM PENH";

    /** Merchant number issued by the acquiring bank. */
    private String merchantId;

    /** The bank behind {@link #accountId}, e.g. {@code ACLEDA Bank Plc}. */
    private String acquiringBank;

    /** Optional; appears in the QR's additional-data block. */
    private String mobileNumber;

    /** Optional branch or outlet label, e.g. {@code Toul Kork}. */
    private String storeLabel;

    /**
     * Optional till label. Defaults to naming this API, so a transaction raised here
     * is distinguishable in the merchant's Bakong statement from one rung up on a
     * physical terminal.
     */
    private String terminalLabel = "phsardigital";

    /**
     * How long a generated QR stays payable.
     *
     * <p>This is written into the QR itself, so banking apps refuse it once the time
     * passes — it is not merely a local convention. Long enough for somebody to open
     * their app and confirm, short enough that an abandoned checkout does not leave a
     * live payment instruction lying around.
     */
    private Duration qrValidity = Duration.ofMinutes(10);

    /**
     * Grace period after {@link #qrValidity} before a pending payment is swept.
     *
     * <p>The sweep asks Bakong about each payment before writing it off, so this only
     * has to cover the gap between a bank debiting the payer and Bakong reporting it.
     */
    private Duration expiryGrace = Duration.ofMinutes(5);

    /** How often {@code PaymentExpirySweeper} runs. */
    private Duration sweepInterval = Duration.ofMinutes(5);

    /**
     * Most payments a single sweep will reconcile. Each one costs a call to Bakong, so
     * the cap bounds what a backlog can do to the rate limit; whatever is left over is
     * picked up by the next run.
     */
    private int sweepBatchSize = 50;

    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Bakong is occasionally slow to answer. This is deliberately longer than the
     * connect timeout and still short enough that a stalled call cannot hold a request
     * thread through a seller's whole checkout.
     */
    private Duration readTimeout = Duration.ofSeconds(15);

    /**
     * Whether payment is actually wired up.
     *
     * <p>False whenever the credentials are absent, which is the normal state of a
     * fresh checkout of this repository and of the test suite. Subscribing then refuses
     * with a clear message instead of generating a QR nobody can pay.
     */
    public boolean isConfigured() {
        return isPresent(apiToken) && isPresent(accountId)
                && isPresent(merchantName) && isPresent(merchantId)
                && isPresent(acquiringBank);
    }

    @PostConstruct
    void validate() {
        if (!isConfigured()) {
            return;
        }
        // The account id is the one field with a shape as well as a length: KHQR
        // insists on exactly one '@', and a QR built on a malformed one is refused by
        // the SDK rather than by Bakong, long after it would have been useful to know.
        if (!accountId.matches("^[^@]+@[^@]+$")) {
            throw new IllegalStateException(
                    "bakong.account-id must look like 'name@bank', but was '" + accountId + "'");
        }
        requireAtMost("bakong.account-id", accountId, MAX_ACCOUNT_FIELD);
        requireAtMost("bakong.merchant-id", merchantId, MAX_ACCOUNT_FIELD);
        requireAtMost("bakong.acquiring-bank", acquiringBank, MAX_ACCOUNT_FIELD);
        requireAtMost("bakong.merchant-name", merchantName, MAX_MERCHANT_NAME);
        requireAtMost("bakong.merchant-city", merchantCity, MAX_MERCHANT_CITY);
    }

    private static void requireAtMost(String name, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalStateException(name + " must be at most " + max
                    + " characters for KHQR, but '" + value + "' is " + value.length() + ".");
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
