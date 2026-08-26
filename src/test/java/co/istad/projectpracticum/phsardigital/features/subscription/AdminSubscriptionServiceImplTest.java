package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.GrantSubscriptionRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanRequest;
import co.istad.projectpracticum.phsardigital.features.subscription.dto.SubscriptionPlanUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSubscriptionServiceImplTest {

    private static final String SELLER_ID = "seller-1";
    private static final String ADMIN_ID = "admin-1";

    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private SellerSubscriptionRepository subscriptionRepository;
    @Mock
    private ListingRepository listingRepository;
    @InjectMocks
    private AdminSubscriptionServiceImpl service;

    @Test
    void createUppercasesTheCodeAndPutsThePlanOnOffer() {
        when(planRepository.existsById("GOLD")).thenReturn(false);
        when(planRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var created = service.createPlan(new SubscriptionPlanRequest(
                    "GOLD", "Gold", new BigDecimal("40.00"), 30, 500, 4));

            assertThat(created.code()).isEqualTo("GOLD");
            assertThat(created.active()).isTrue();
            assertThat(created.listingLimit()).isEqualTo(500);
        }
    }

    @Test
    void createRefusesACodeThatAlreadyExists() {
        when(planRepository.existsById("BASIC")).thenReturn(true);

        assertThatThrownBy(() -> service.createPlan(new SubscriptionPlanRequest(
                "BASIC", "Basic again", new BigDecimal("1.00"), 30, 5, 9)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(planRepository, never()).save(any());
    }

    @Test
    void updateAppliesOnlyTheFieldsPresentOnTheRequest() {
        SubscriptionPlan basic = plan("BASIC", "Basic", "5.00", 30, 20);
        when(planRepository.findById("BASIC")).thenReturn(Optional.of(basic));
        when(planRepository.save(basic)).thenReturn(basic);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var updated = service.updatePlan("BASIC", new SubscriptionPlanUpdateRequest(
                    null, new BigDecimal("7.50"), null, null, null));

            assertThat(updated.priceUsd()).isEqualByComparingTo("7.50");
            // Untouched by a null field.
            assertThat(updated.displayName()).isEqualTo("Basic");
            assertThat(updated.durationDays()).isEqualTo(30);
            assertThat(updated.listingLimit()).isEqualTo(20);
        }
    }

    @Test
    void unlimitedIsPublishedAsNullRatherThanTheSentinel() {
        SubscriptionPlan premium = plan("PREMIUM", "Premium", "25.00", 30,
                SubscriptionPlan.UNLIMITED_LISTINGS);
        when(planRepository.findById("PREMIUM")).thenReturn(Optional.of(premium));
        when(planRepository.save(premium)).thenReturn(premium);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var updated = service.updatePlan("PREMIUM",
                    new SubscriptionPlanUpdateRequest(null, null, null, null, 7));

            assertThat(updated.listingLimit()).isNull();
        }
    }

    @Test
    void deactivatingAnAlreadyRetiredPlanConflicts() {
        SubscriptionPlan basic = plan("BASIC", "Basic", "5.00", 30, 20);
        basic.setActive(false);
        when(planRepository.findById("BASIC")).thenReturn(Optional.of(basic));

        assertThatThrownBy(() -> service.deactivatePlan("BASIC"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    /** Retiring a plan must not disturb the sellers already inside a period on it. */
    @Test
    void deactivatingLeavesCurrentSubscribersOnThePlan()  {
        SubscriptionPlan basic = plan("BASIC", "Basic", "5.00", 30, 20);
        when(planRepository.findById("BASIC")).thenReturn(Optional.of(basic));
        when(planRepository.save(basic)).thenReturn(basic);
        when(subscriptionRepository.existsByPlanCodeAndStatus("BASIC", SubscriptionStatus.ACTIVE))
                .thenReturn(true);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var retired = service.deactivatePlan("BASIC");

            assertThat(retired.active()).isFalse();
        }

        verify(subscriptionRepository, never()).deleteById(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void grantExtendsAnUnexpiredPeriodInsteadOfRestartingIt() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(10);
        SellerSubscription existing = subscription("BASIC", expiry);
        when(planRepository.findById("PREMIUM")).thenReturn(Optional.of(
                plan("PREMIUM", "Premium", "25.00", 30, SubscriptionPlan.UNLIMITED_LISTINGS)));
        when(subscriptionRepository.findById(SELLER_ID)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(existing)).thenReturn(existing);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var granted = service.grant(SELLER_ID,
                    new GrantSubscriptionRequest("PREMIUM", null, null));

            assertThat(granted.planCode()).isEqualTo("PREMIUM");
            // Days already paid for are kept: 10 remaining + the plan's own 30.
            assertThat(granted.expiresAt()).isEqualTo(expiry.plusDays(30));
        }
    }

    @Test
    void grantCanRestartFromTodayAndOverrideTheDuration() {
        SellerSubscription existing = subscription("BASIC", LocalDateTime.now().plusDays(10));
        when(planRepository.findById("BASIC")).thenReturn(Optional.of(
                plan("BASIC", "Basic", "5.00", 30, 20)));
        when(subscriptionRepository.findById(SELLER_ID)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(existing)).thenReturn(existing);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var granted = service.grant(SELLER_ID,
                    new GrantSubscriptionRequest("BASIC", 7, false));

            assertThat(granted.expiresAt()).isAfter(LocalDateTime.now().plusDays(6));
            assertThat(granted.expiresAt()).isBefore(LocalDateTime.now().plusDays(8));
        }
    }

    /** An admin restoring somebody to a legacy plan is deliberate, not a mistake. */
    @Test
    void grantAllowsARetiredPlanEvenThoughSubscribeWouldNot() {
        SubscriptionPlan retired = plan("LEGACY", "Legacy", "3.00", 30, 10);
        retired.setActive(false);
        when(planRepository.findById("LEGACY")).thenReturn(Optional.of(retired));
        when(subscriptionRepository.findById(SELLER_ID)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            assertThat(service.grant(SELLER_ID,
                    new GrantSubscriptionRequest("LEGACY", null, null)).planCode())
                    .isEqualTo("LEGACY");
        }
    }

    @Test
    void grantRefusesAnUnknownPlan() {
        when(planRepository.findById("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(SELLER_ID,
                new GrantSubscriptionRequest("NOPE", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * The gates judge against the clock as well as the stored status, so a cancel that
     * left the old expiry in place would keep letting the seller post.
     */
    @Test
    void cancelPullsTheExpiryBackSoTheGatesCloseImmediately() {
        SellerSubscription existing = subscription("BASIC", LocalDateTime.now().plusDays(20));
        when(subscriptionRepository.findById(SELLER_ID)).thenReturn(Optional.of(existing));

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var cancelled = service.cancel(SELLER_ID);

            assertThat(cancelled.status()).isEqualTo(SubscriptionStatus.CANCELLED);
            assertThat(cancelled.expiresAt()).isBeforeOrEqualTo(LocalDateTime.now());
            assertThat(cancelled.canChat()).isFalse();
            assertThat(cancelled.canPostListing()).isFalse();
        }
    }

    @Test
    void cancellingTwiceConflicts() {
        SellerSubscription existing = subscription("BASIC", LocalDateTime.now());
        existing.setStatus(SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findById(SELLER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel(SELLER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    private static MockedStatic<AuthUtils> authenticatedAdmin() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(ADMIN_ID);
        return auth;
    }

    private static SubscriptionPlan plan(String code, String displayName, String price,
                                         int durationDays, int listingLimit) {
        SubscriptionPlan plan = new SubscriptionPlan(code);
        plan.setDisplayName(displayName);
        plan.setPriceUsd(new BigDecimal(price));
        plan.setDurationDays(durationDays);
        plan.setListingLimit(listingLimit);
        plan.setActive(true);
        return plan;
    }

    private static SellerSubscription subscription(String planCode, LocalDateTime expiresAt) {
        SellerSubscription subscription = new SellerSubscription(SELLER_ID);
        subscription.setPlanCode(planCode);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(LocalDateTime.now().minusDays(20));
        subscription.setExpiresAt(expiresAt);
        return subscription;
    }
}
