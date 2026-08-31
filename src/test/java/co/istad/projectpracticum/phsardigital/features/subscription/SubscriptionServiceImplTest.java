package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.payments.Payment;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentCurrency;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentPurpose;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentService;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscribeRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceImplTest {

    private static final String SELLER_ID = "seller-1";

    @Mock
    private SellerSubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private SellerAccessGuard sellerAccessGuard;
    @Mock
    private SubscriptionActivation activation;
    @Mock
    private PaymentService paymentService;
    @InjectMocks
    private SubscriptionServiceImpl service;

    @Test
    void thePricingPageShowsOnlyPlansStillOnOffer() {
        when(planRepository.findAllByActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(plan("BASIC", "Basic", 20)));

        var plans = service.listPlans();

        assertThat(plans).singleElement().satisfies(plan -> {
            assertThat(plan.code()).isEqualTo("BASIC");
            assertThat(plan.listingLimit()).isEqualTo(20);
        });
        verify(planRepository, never()).findAllByOrderBySortOrderAsc();
    }

    @Test
    void subscribingRefusesARetiredPlanEvenWhenItsCodeIsKnown() {
        SubscriptionPlan retired = plan("LEGACY", "Legacy", 10);
        retired.setActive(false);
        when(planRepository.findById("LEGACY")).thenReturn(Optional.of(retired));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThatThrownBy(() -> service.subscribe(new SubscribeRequest("LEGACY")))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT));
        }

        verifyNoInteractions(paymentService);
        verify(activation, never()).activate(any(), any());
    }

    @Test
    void anUnknownPlanCodeIsTheCallersMistakeNotABrokenCatalogue() {
        when(planRepository.findById("NOPE")).thenReturn(Optional.empty());

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThatThrownBy(() -> service.subscribe(new SubscribeRequest("NOPE")))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        verifyNoInteractions(paymentService);
    }

    @Test
    void subscribingIssuesAQrAndGrantsNothingUntilItIsPaid() {
        SubscriptionPlan premium = plan("PREMIUM", "Premium", -1);
        premium.setPriceUsd(new BigDecimal("12.50"));
        when(planRepository.findById("PREMIUM")).thenReturn(Optional.of(premium));
        when(paymentService.start(eq(PaymentPurpose.SUBSCRIPTION), eq("PREMIUM"),
                eq(SELLER_ID), eq(new BigDecimal("12.50")), eq(PaymentCurrency.USD)))
                .thenReturn(pendingPayment("PREMIUM", new BigDecimal("12.50")));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var result = service.subscribe(new SubscribeRequest("PREMIUM"));

            assertThat(result.paymentRequired()).isTrue();
            assertThat(result.planCode()).isEqualTo("PREMIUM");
            assertThat(result.payment()).isNotNull();
            assertThat(result.payment().status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.payment().qr()).isNotBlank();
            // The whole point: nothing is granted on the strength of asking.
            assertThat(result.subscription()).isNull();
        }

        verify(activation, never()).activate(any(), any());
    }

    @Test
    void aFreePlanIsGrantedOutrightRatherThanSendingAnyoneToPayNothing() {
        SubscriptionPlan free = plan("FREE", "Free", 3);
        free.setPriceUsd(BigDecimal.ZERO);
        when(planRepository.findById("FREE")).thenReturn(Optional.of(free));
        when(activation.activate(SELLER_ID, free)).thenReturn(activeSubscription("FREE"));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var result = service.subscribe(new SubscribeRequest("FREE"));

            assertThat(result.paymentRequired()).isFalse();
            assertThat(result.payment()).isNull();
            assertThat(result.subscription()).isNotNull();
            assertThat(result.subscription().planCode()).isEqualTo("FREE");
        }

        verifyNoInteractions(paymentService);
    }

    @Test
    void aSuspendedShopCannotBuyItsWayBackIn() {
        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "suspended"))
                    .when(sellerAccessGuard).requireActiveSeller(SELLER_ID);

            assertThatThrownBy(() -> service.subscribe(new SubscribeRequest("BASIC")))
                    .isInstanceOf(ResponseStatusException.class);
        }

        // Refused before a QR exists, so there is nothing for them to pay and no
        // half-finished payment left behind.
        verifyNoInteractions(paymentService);
        verify(activation, never()).activate(any(), any());
    }

    private static MockedStatic<AuthUtils> authenticatedSeller() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(SELLER_ID);
        return auth;
    }

    private static Payment pendingPayment(String planCode, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setUuid(UUID.randomUUID());
        payment.setPurpose(PaymentPurpose.SUBSCRIPTION);
        payment.setReference(planCode);
        payment.setPayerId(SELLER_ID);
        payment.setAmount(amount);
        payment.setCurrency(PaymentCurrency.USD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setQr("00020101021229...6304ABCD");
        payment.setMd5("0123456789abcdef0123456789abcdef");
        payment.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return payment;
    }

    private static SellerSubscription activeSubscription(String planCode) {
        SellerSubscription subscription = new SellerSubscription(SELLER_ID);
        subscription.setPlanCode(planCode);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(LocalDateTime.now());
        subscription.setExpiresAt(LocalDateTime.now().plusDays(30));
        return subscription;
    }

    private static SubscriptionPlan plan(String code, String displayName, int listingLimit) {
        SubscriptionPlan plan = new SubscriptionPlan(code);
        plan.setDisplayName(displayName);
        plan.setPriceUsd(new BigDecimal("5.00"));
        plan.setDurationDays(30);
        plan.setListingLimit(listingLimit);
        plan.setActive(true);
        return plan;
    }
}
