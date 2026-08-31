package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongClient;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongTransaction;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.KhqrGenerator;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.KhqrPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    private static final String SELLER_ID = "seller-1";
    private static final BigDecimal PRICE = new BigDecimal("12.50");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentSettler paymentSettler;
    @Mock
    private KhqrGenerator khqrGenerator;
    @Mock
    private BakongClient bakongClient;
    @Mock
    private co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongProps props;
    @InjectMocks
    private PaymentServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void platformCollectsToItsOwnAccount() {
        when(props.getAccountId()).thenReturn("ratanak_thai@bkrt");
    }

    @Test
    void pressingSubscribeTwiceReturnsTheSameQrRatherThanMintingASecond() {
        // Two live QRs for one purchase is worse than it sounds: only one of them is
        // the row the server is watching, and the seller cannot tell which.
        Payment open = pending(LocalDateTime.now().plusMinutes(8));
        when(paymentRepository.findFirstByPayerIdAndPurposeAndReferenceAndStatusOrderByCreatedAtDesc(
                SELLER_ID, PaymentPurpose.SUBSCRIPTION, "PREMIUM", PaymentStatus.PENDING))
                .thenReturn(Optional.of(open));
        when(paymentSettler.handles(PaymentPurpose.SUBSCRIPTION)).thenReturn(true);

        Payment result = service.start(PaymentPurpose.SUBSCRIPTION, "PREMIUM", SELLER_ID,
                PRICE, PaymentCurrency.USD);

        assertThat(result.getUuid()).isEqualTo(open.getUuid());
        verifyNoInteractions(khqrGenerator);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void aQrThatLapsedBetweenThePressesIsNotHandedBack() {
        // Its expiry is baked into the payload, so no banking app would accept it.
        Payment stale = pending(LocalDateTime.now().minusMinutes(1));
        when(paymentRepository.findFirstByPayerIdAndPurposeAndReferenceAndStatusOrderByCreatedAtDesc(
                SELLER_ID, PaymentPurpose.SUBSCRIPTION, "PREMIUM", PaymentStatus.PENDING))
                .thenReturn(Optional.of(stale));
        when(paymentSettler.handles(PaymentPurpose.SUBSCRIPTION)).thenReturn(true);
        when(khqrGenerator.generate(any(), any(), any()))
                .thenReturn(new KhqrPayload("00020101021229fresh6304ABCD", "freshmd5",
                        LocalDateTime.now().plusMinutes(10)));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));

        Payment result = service.start(PaymentPurpose.SUBSCRIPTION, "PREMIUM", SELLER_ID,
                PRICE, PaymentCurrency.USD);

        assertThat(result.getUuid()).isNotEqualTo(stale.getUuid());
        assertThat(result.getMd5()).isEqualTo("freshmd5");
    }

    @Test
    void aPurposeNothingCanFulfilNeverGetsAQr() {
        // Taking money for something that cannot be granted is the one mistake here
        // that a retry does not fix.
        when(paymentSettler.handles(PaymentPurpose.SUBSCRIPTION)).thenReturn(false);

        assertThatThrownBy(() -> service.start(PaymentPurpose.SUBSCRIPTION, "PREMIUM",
                SELLER_ID, PRICE, PaymentCurrency.USD))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(khqrGenerator);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void verifyingAnUnpaidQrLeavesItPendingWithoutSettlingAnything() {
        Payment payment = pending(LocalDateTime.now().plusMinutes(8));
        when(paymentRepository.findById(payment.getUuid())).thenReturn(Optional.of(payment));
        when(bakongClient.checkByMd5(payment.getMd5())).thenReturn(BakongTransaction.unsettled());

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var result = service.verify(payment.getUuid());

            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        }

        verify(paymentSettler, never()).settle(any(), any());
    }

    @Test
    void anAlreadyPaidQrIsNotReCheckedAgainstBakong() {
        Payment payment = pending(LocalDateTime.now().plusMinutes(8));
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        when(paymentRepository.findById(payment.getUuid())).thenReturn(Optional.of(payment));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThat(service.verify(payment.getUuid()).status()).isEqualTo(PaymentStatus.PAID);
        }

        verifyNoInteractions(bakongClient);
        verify(paymentSettler, never()).settle(any(), any());
    }

    @Test
    void aConfirmedTransferIsHandedToTheSettler() {
        Payment payment = pending(LocalDateTime.now().plusMinutes(8));
        BakongTransaction paid = new BakongTransaction(true, "hash-1", "payer@aclb",
                "ratanak_thai@bkrt", PRICE, "USD");
        Payment settled = pending(payment.getExpiresAt());
        settled.setStatus(PaymentStatus.PAID);

        when(paymentRepository.findById(payment.getUuid())).thenReturn(Optional.of(payment));
        when(bakongClient.checkByMd5(payment.getMd5())).thenReturn(paid);
        when(paymentSettler.settle(payment.getUuid(), paid)).thenReturn(settled);

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThat(service.verify(payment.getUuid()).status()).isEqualTo(PaymentStatus.PAID);
        }

        verify(paymentSettler).settle(payment.getUuid(), paid);
    }

    @Test
    void somebodyElsesPaymentIsNotFoundRatherThanForbidden() {
        // Whether a given UUID names a real payment is not something an unrelated
        // caller should be able to establish.
        Payment payment = pending(LocalDateTime.now().plusMinutes(8));
        payment.setPayerId("another-seller");
        when(paymentRepository.findById(payment.getUuid())).thenReturn(Optional.of(payment));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThatThrownBy(() -> service.verify(payment.getUuid()))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        verifyNoInteractions(bakongClient);
    }

    private static MockedStatic<AuthUtils> authenticatedSeller() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(SELLER_ID);
        return auth;
    }

    private static Payment pending(LocalDateTime expiresAt) {
        Payment payment = new Payment();
        payment.setUuid(UUID.randomUUID());
        payment.setPurpose(PaymentPurpose.SUBSCRIPTION);
        payment.setReference("PREMIUM");
        payment.setPayerId(SELLER_ID);
        payment.setAmount(PRICE);
        payment.setCurrency(PaymentCurrency.USD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCollectingAccountId("ratanak_thai@bkrt");
        payment.setQr("00020101021229...6304ABCD");
        payment.setMd5("0123456789abcdef0123456789abcdef");
        payment.setExpiresAt(expiresAt);
        return payment;
    }
}
