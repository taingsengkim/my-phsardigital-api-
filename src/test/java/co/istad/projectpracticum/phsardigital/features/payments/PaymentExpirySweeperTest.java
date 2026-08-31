package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongClient;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongProps;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentExpirySweeperTest {

    private PaymentRepository paymentRepository;
    private PaymentSettler paymentSettler;
    private BakongClient bakongClient;
    private PaymentExpirySweeper sweeper;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        paymentSettler = mock(PaymentSettler.class);
        bakongClient = mock(BakongClient.class);

        BakongProps props = new BakongProps();
        props.setApiToken("test-token");
        props.setAccountId("ratanak_thai@bkrt");
        props.setMerchantName("Anajak Store");
        props.setMerchantId("1516169");
        props.setAcquiringBank("ACLEDA Bank Plc");

        sweeper = new PaymentExpirySweeper(paymentRepository, paymentSettler, bakongClient, props);
    }

    @Test
    void aPaymentBakongHasNoRecordOfIsWrittenOff() {
        Payment lapsed = lapsed();
        givenLapsed(lapsed);
        when(bakongClient.checkByMd5(lapsed.getMd5())).thenReturn(BakongTransaction.unsettled());

        sweeper.sweep();

        verify(paymentSettler).expire(lapsed.getUuid());
        verify(paymentSettler, never()).settle(any(), any());
    }

    @Test
    void somebodyWhoPaidThenClosedTheirBrowserIsStillHonoured() {
        // Nothing polls for them, so this sweep is the only thing that will ever
        // notice. Expiring on the clock alone would take their money for nothing.
        Payment lapsed = lapsed();
        BakongTransaction paid = new BakongTransaction(true, "hash-1", "payer@aclb",
                "ratanak_thai@bkrt", new BigDecimal("12.50"), "USD");
        givenLapsed(lapsed);
        when(bakongClient.checkByMd5(lapsed.getMd5())).thenReturn(paid);

        sweeper.sweep();

        verify(paymentSettler).settle(lapsed.getUuid(), paid);
        verify(paymentSettler, never()).expire(any());
    }

    @Test
    void bakongBeingUnreachableLeavesThePaymentPendingForTheNextSweep() {
        // An outage is not evidence that nobody paid, and expiring on the strength of
        // one would be irreversible.
        Payment lapsed = lapsed();
        givenLapsed(lapsed);
        when(bakongClient.checkByMd5(lapsed.getMd5()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "down"));

        sweeper.sweep();

        verify(paymentSettler, never()).expire(any());
        verify(paymentSettler, never()).settle(any(), any());
    }

    @Test
    void oneUnreachablePaymentDoesNotStopTheRestOfTheBatch() {
        Payment broken = lapsed();
        Payment fine = lapsed();
        when(paymentRepository.findLapsed(eq(PaymentStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(List.of(broken, fine));
        when(bakongClient.checkByMd5(broken.getMd5()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "down"));
        when(bakongClient.checkByMd5(fine.getMd5())).thenReturn(BakongTransaction.unsettled());

        sweeper.sweep();

        verify(paymentSettler).expire(fine.getUuid());
    }

    @Test
    void anUnconfiguredServerSweepsNothing() {
        PaymentExpirySweeper idle = new PaymentExpirySweeper(
                paymentRepository, paymentSettler, bakongClient, new BakongProps());

        idle.sweep();

        verify(paymentRepository, never()).findLapsed(any(), any(), any());
    }

    private void givenLapsed(Payment payment) {
        when(paymentRepository.findLapsed(eq(PaymentStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(List.of(payment));
    }

    private static Payment lapsed() {
        Payment payment = new Payment();
        payment.setUuid(UUID.randomUUID());
        payment.setPurpose(PaymentPurpose.SUBSCRIPTION);
        payment.setReference("PREMIUM");
        payment.setPayerId("seller-1");
        payment.setAmount(new BigDecimal("12.50"));
        payment.setCurrency(PaymentCurrency.USD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setQr("00020101021229...6304ABCD");
        payment.setMd5(UUID.randomUUID().toString().replace("-", ""));
        payment.setExpiresAt(LocalDateTime.now().minusHours(1));
        return payment;
    }
}
