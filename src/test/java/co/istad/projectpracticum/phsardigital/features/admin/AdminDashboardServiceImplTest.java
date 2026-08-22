package co.istad.projectpracticum.phsardigital.features.admin;

import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileRepository;
import co.istad.projectpracticum.phsardigital.features.seller.application.ApplicationStatus;
import co.istad.projectpracticum.phsardigital.features.seller.application.SellerApplicationRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SellerSubscriptionRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionStatus;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-22T08:30:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    @Mock
    private UserProfileRepository userRepository;
    @Mock
    private SellerProfileRepository sellerProfileRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private SellerApplicationRepository applicationRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private SellerSubscriptionRepository subscriptionRepository;

    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardServiceImpl(
                userRepository,
                sellerProfileRepository,
                listingRepository,
                applicationRepository,
                purchaseRepository,
                subscriptionRepository,
                Clock.fixed(NOW, ZONE));
    }

    @Test
    void buildsGroupedSummaryFromOneFixedEvaluationTime() {
        when(userRepository.count()).thenReturn(41L);
        when(sellerProfileRepository.count()).thenReturn(12L);
        when(sellerProfileRepository.countByIsActiveTrue()).thenReturn(9L);
        when(listingRepository.count()).thenReturn(125L);
        when(listingRepository.countByStatusAndSellerProfile_IsActiveTrue(ListingStatus.ACTIVE))
                .thenReturn(87L);
        when(applicationRepository.countByStatus(ApplicationStatus.PENDING)).thenReturn(4L);
        when(purchaseRepository.countByStatus(PurchaseStatus.COMPLETED)).thenReturn(31L);
        when(purchaseRepository.sumRoundedTotalPriceByStatus("COMPLETED"))
                .thenReturn(new BigDecimal("1234.50"));

        LocalDateTime expectedEvaluationTime = LocalDateTime.ofInstant(NOW, ZONE);
        when(subscriptionRepository.countActiveByPlan(
                SubscriptionStatus.ACTIVE, expectedEvaluationTime))
                .thenReturn(List.of(
                        new Object[]{SubscriptionPlan.BASIC, 2L},
                        new Object[]{SubscriptionPlan.PREMIUM, 1L}));

        var result = service.getSummary();

        assertThat(result.users().total()).isEqualTo(41L);
        assertThat(result.sellers().total()).isEqualTo(12L);
        assertThat(result.sellers().active()).isEqualTo(9L);
        assertThat(result.listings().total()).isEqualTo(125L);
        assertThat(result.listings().publiclyAvailable()).isEqualTo(87L);
        assertThat(result.applications().pending()).isEqualTo(4L);
        assertThat(result.orders().completed()).isEqualTo(31L);
        assertThat(result.orders().completedGmv().amount())
                .isEqualByComparingTo("1234.50");
        assertThat(result.orders().completedGmv().currencyCode()).isEqualTo("USD");
        assertThat(result.asOf()).isEqualTo(NOW);

        assertThat(result.subscriptions().active()).isEqualTo(3L);
        assertThat(result.subscriptions().byPlan())
                .extracting(plan -> plan.code(), plan -> plan.displayName(), plan -> plan.count())
                .containsExactly(
                        tuple("BASIC", "Basic", 2L),
                        tuple("STANDARD", "Standard", 0L),
                        tuple("PREMIUM", "Premium", 1L));

        verify(listingRepository)
                .countByStatusAndSellerProfile_IsActiveTrue(ListingStatus.ACTIVE);
        verify(listingRepository, never()).countByStatus(ListingStatus.ACTIVE);
        verify(purchaseRepository).countByStatus(PurchaseStatus.COMPLETED);
        verify(purchaseRepository).sumRoundedTotalPriceByStatus("COMPLETED");
        verify(subscriptionRepository).countActiveByPlan(
                SubscriptionStatus.ACTIVE, expectedEvaluationTime);
    }

    @Test
    void returnsZeroGmvAndEveryPlanWhenThereIsNoActivity() {
        when(subscriptionRepository.countActiveByPlan(
                SubscriptionStatus.ACTIVE, LocalDateTime.ofInstant(NOW, ZONE)))
                .thenReturn(List.of());
        when(purchaseRepository.sumRoundedTotalPriceByStatus("COMPLETED"))
                .thenReturn(null);

        var result = service.getSummary();

        assertThat(result.orders().completedGmv().amount()).isEqualByComparingTo("0.00");
        assertThat(result.orders().completedGmv().currencyCode()).isEqualTo("USD");
        assertThat(result.subscriptions().active()).isZero();
        assertThat(result.subscriptions().byPlan())
                .extracting(plan -> plan.code(), plan -> plan.count())
                .containsExactly(
                        tuple("BASIC", 0L),
                        tuple("STANDARD", 0L),
                        tuple("PREMIUM", 0L));
    }
}
