package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

        verify(subscriptionRepository, never()).save(any());
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
    }

    @Test
    void changingPlanMidPeriodKeepsTheDaysAlreadyPaidFor() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(12);
        SellerSubscription existing = new SellerSubscription(SELLER_ID);
        existing.setPlanCode("BASIC");
        existing.setStatus(SubscriptionStatus.ACTIVE);
        existing.setStartedAt(LocalDateTime.now().minusDays(18));
        existing.setExpiresAt(expiry);

        when(planRepository.findById("PREMIUM")).thenReturn(Optional.of(plan("PREMIUM", "Premium", -1)));
        when(subscriptionRepository.findById(SELLER_ID)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(existing)).thenReturn(existing);

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var result = service.subscribe(new SubscribeRequest("PREMIUM"));

            assertThat(result.planCode()).isEqualTo("PREMIUM");
            assertThat(result.expiresAt()).isEqualTo(expiry.plusDays(30));
            assertThat(result.listingLimit()).isNull();
        }
    }

    @Test
    void aSuspendedShopCannotBuyItsWayBackIn() {
        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "suspended"))
                    .when(sellerAccessGuard).requireActiveSeller(SELLER_ID);

            assertThatThrownBy(() -> service.subscribe(new SubscribeRequest("BASIC")))
                    .isInstanceOf(ResponseStatusException.class);
        }

        verify(subscriptionRepository, never()).save(any());
    }

    private static MockedStatic<AuthUtils> authenticatedSeller() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(SELLER_ID);
        return auth;
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
