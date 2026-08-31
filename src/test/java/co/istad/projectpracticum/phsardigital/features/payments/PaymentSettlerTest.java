package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongProps;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentSettlerTest {

    private static final String MERCHANT_ACCOUNT = "ratanak_thai@bkrt";

    private PaymentRepository paymentRepository;
    private RecordingSettlement settlement;
    private PaymentSettler settler;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(call -> call.getArgument(0));

        BakongProps props = new BakongProps();
        props.setAccountId(MERCHANT_ACCOUNT);

        settlement = new RecordingSettlement();
        settler = new PaymentSettler(paymentRepository, props, List.of(settlement));
    }

    @Test
    void settlingMarksThePaymentPaidAndGrantsWhatItBought() {
        Payment payment = pending(new BigDecimal("12.50"));
        given(payment);

        Payment settled = settler.settle(payment.getUuid(), paidWith(new BigDecimal("12.50")));

        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(settled.getPaidAt()).isNotNull();
        assertThat(settled.getBakongHash()).isEqualTo("hash-1");
        assertThat(settled.getFromAccountId()).isEqualTo("payer@aclb");
        assertThat(settlement.settled).containsExactly(payment.getUuid());
    }

    @Test
    void aPaymentAlreadySettledIsNotGrantedTwice() {
        // The shape of two clients polling at once: both hear "paid" from Bakong, both
        // arrive here, and only the first may hand out a subscription.
        Payment payment = pending(new BigDecimal("12.50"));
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now().minusMinutes(1));
        given(payment);

        settler.settle(payment.getUuid(), paidWith(new BigDecimal("12.50")));

        assertThat(settlement.settled).isEmpty();
    }

    @Test
    void aPaymentThatArrivedAfterItsQrLapsedIsStillHonoured() {
        // The money moved. What this table decided about the QR in the meantime does
        // not get to keep it.
        Payment payment = pending(new BigDecimal("12.50"));
        payment.setStatus(PaymentStatus.EXPIRED);
        payment.setExpiresAt(LocalDateTime.now().minusHours(2));
        given(payment);

        Payment settled = settler.settle(payment.getUuid(), paidWith(new BigDecimal("12.50")));

        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(settlement.settled).containsExactly(payment.getUuid());
    }

    @Test
    void aTransferOfTheWrongAmountGrantsNothing() {
        Payment payment = pending(new BigDecimal("12.50"));
        given(payment);

        assertThatThrownBy(() -> settler.settle(payment.getUuid(), paidWith(new BigDecimal("1.00"))))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(settlement.settled).isEmpty();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void aTransferToSomeOtherAccountGrantsNothing() {
        Payment payment = pending(new BigDecimal("12.50"));
        given(payment);

        BakongTransaction elsewhere = new BakongTransaction(true, "hash-1", "payer@aclb",
                "someone_else@bank", new BigDecimal("12.50"), "USD");

        assertThatThrownBy(() -> settler.settle(payment.getUuid(), elsewhere))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(settlement.settled).isEmpty();
    }

    @Test
    void anAmountBakongDidNotReportIsNotTreatedAsDisagreement() {
        // A field missing from their response is a gap in it, not evidence the wrong
        // sum was sent — and the MD5 already pins the amount.
        Payment payment = pending(new BigDecimal("12.50"));
        given(payment);

        BakongTransaction sparse = new BakongTransaction(true, "hash-1", "payer@aclb",
                null, null, null);

        assertThat(settler.settle(payment.getUuid(), sparse).getStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(settlement.settled).containsExactly(payment.getUuid());
    }

    @Test
    void twoHandlersForOnePurposeRefuseToStart() {
        // Which one would run is down to bean ordering, and the loser silently never
        // grants anything. Better to fail at startup than to find out in production.
        assertThatThrownBy(() -> new PaymentSettler(paymentRepository, new BakongProps(),
                List.of(new RecordingSettlement(), new RecordingSettlement())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUBSCRIPTION");
    }

    private void given(Payment payment) {
        when(paymentRepository.findByUuidForUpdate(payment.getUuid()))
                .thenReturn(Optional.of(payment));
    }

    private static BakongTransaction paidWith(BigDecimal amount) {
        return new BakongTransaction(true, "hash-1", "payer@aclb",
                MERCHANT_ACCOUNT, amount, "USD");
    }

    private static Payment pending(BigDecimal amount) {
        Payment payment = new Payment();
        payment.setUuid(UUID.randomUUID());
        payment.setPurpose(PaymentPurpose.SUBSCRIPTION);
        payment.setReference("PREMIUM");
        payment.setPayerId("seller-1");
        payment.setAmount(amount);
        payment.setCurrency(PaymentCurrency.USD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setQr("00020101021229...6304ABCD");
        payment.setMd5("0123456789abcdef0123456789abcdef");
        payment.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return payment;
    }

    /** Stands in for the subscription feature, and remembers what it was asked to grant. */
    private static final class RecordingSettlement implements PaymentSettlement {
        private final List<UUID> settled = new ArrayList<>();

        @Override
        public PaymentPurpose purpose() {
            return PaymentPurpose.SUBSCRIPTION;
        }

        @Override
        public void settle(Payment payment) {
            settled.add(payment.getUuid());
        }
    }
}
