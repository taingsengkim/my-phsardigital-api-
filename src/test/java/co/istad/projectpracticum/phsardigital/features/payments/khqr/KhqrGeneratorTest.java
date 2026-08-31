package co.istad.projectpracticum.phsardigital.features.payments.khqr;

import co.istad.projectpracticum.phsardigital.features.payments.PaymentCurrency;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.KHQRDecodeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real NBC SDK rather than a stand-in. The whole reason that dependency
 * is here is that a malformed payload is rejected by a banking app rather than by us,
 * so a test that mocked the generation away would check nothing worth checking.
 */
class KhqrGeneratorTest {

    private KhqrGenerator generator;

    @BeforeEach
    void setUp() {
        BakongProps props = new BakongProps();
        props.setApiToken("test-token");
        props.setAccountId("ratanak_thai@bkrt");
        props.setMerchantName("Anajak Store");
        props.setMerchantCity("PHNOM PENH");
        props.setMerchantId("1516169");
        props.setAcquiringBank("ACLEDA Bank Plc");
        props.setMobileNumber("855964735982");
        props.setStoreLabel("Toul Kork");
        generator = new KhqrGenerator(props);
    }

    @Test
    void theGeneratedPayloadIsAValidKhqrCarryingTheAmountAndOurAccount() {
        KhqrPayload payload = generator.generate(
                UUID.randomUUID(), new BigDecimal("12.50"), PaymentCurrency.USD);

        assertThat(BakongKHQR.verify(payload.qr()).getData().isValid())
                .as("CRC must check out, or every banking app refuses the code")
                .isTrue();

        KHQRDecodeData decoded = BakongKHQR.decode(payload.qr()).getData();
        assertThat(decoded.getBakongAccountID()).isEqualTo("ratanak_thai@bkrt");
        assertThat(decoded.getMerchantName()).isEqualTo("Anajak Store");
        assertThat(new BigDecimal(decoded.getTransactionAmount()))
                .isEqualByComparingTo("12.50");
        // 840 is USD; a dynamic code (12) is one that names a sum rather than asking
        // the payer to type one.
        assertThat(decoded.getTransactionCurrency()).isEqualTo("840");
        assertThat(decoded.getPointOfInitiationMethod()).isEqualTo("12");
    }

    @Test
    void everyPayloadCarriesAnExpiryBecauseADynamicCodeCannotBeBuiltWithoutOne() {
        KhqrPayload payload = generator.generate(
                UUID.randomUUID(), new BigDecimal("1.00"), PaymentCurrency.USD);

        assertThat(payload.expiresAt()).isAfter(java.time.LocalDateTime.now());
        assertThat(BakongKHQR.decode(payload.qr()).getData().getExpirationTimestamp())
                .isNotNull();
    }

    @Test
    void twoPaymentsForTheSamePriceHashDifferently() {
        // This is the property the whole confirmation design rests on. The MD5 covers
        // the entire payload, so if two payments produced identical payloads, settling
        // one would settle the other — a seller would get two plans for one payment.
        KhqrPayload first = generator.generate(
                UUID.randomUUID(), new BigDecimal("12.50"), PaymentCurrency.USD);
        KhqrPayload second = generator.generate(
                UUID.randomUUID(), new BigDecimal("12.50"), PaymentCurrency.USD);

        assertThat(first.md5()).isNotEqualTo(second.md5());
    }

    @Test
    void theBillNumberFitsWhatKhqrAllowsAndStaysUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String billNumber = KhqrGenerator.billNumberFor(UUID.randomUUID());
            // 25 characters is the KHQR ceiling; base 36 over 128 bits needs exactly
            // that at most, which is why the encoding was chosen.
            assertThat(billNumber).hasSizeLessThanOrEqualTo(25).matches("[0-9A-Z]+");
            assertThat(seen.add(billNumber)).isTrue();
        }
    }

    @Test
    void rielCannotCarryFractionsAndIsRefusedBeforeTheSdkSeesIt() {
        assertThatThrownBy(() -> generator.generate(
                UUID.randomUUID(), new BigDecimal("1200.50"), PaymentCurrency.KHR))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("decimal");
    }

    @Test
    void anUnconfiguredServerRefusesToMintAQrNobodyCouldPay() {
        KhqrGenerator unconfigured = new KhqrGenerator(new BakongProps());

        assertThatThrownBy(() -> unconfigured.generate(
                UUID.randomUUID(), new BigDecimal("12.50"), PaymentCurrency.USD))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not configured");
    }
}
