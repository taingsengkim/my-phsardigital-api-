package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import co.istad.projectpracticum.phsardigital.features.user.dto.AdminBuyerResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.ModerateBuyerRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBuyerServiceImplTest {

    private static final String BUYER_ID = "buyer-1";
    private static final String ADMIN_ID = "admin-1";

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private UserProfileMapper userProfileMapper;
    @InjectMocks
    private AdminBuyerServiceImpl service;

    @Test
    void listsBuyersNewestFirstAndJoinsTheirSettledOrderTotals() {
        UserProfile withOrders = buyer(BUYER_ID);
        UserProfile withoutOrders = buyer("buyer-2");
        when(userProfileRepository.findAll(
                ArgumentMatchers.<Specification<UserProfile>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withOrders, withoutOrders)));
        when(purchaseRepository.orderStatsForBuyers(
                PurchaseStatus.COMPLETED.name(), List.of(BUYER_ID, "buyer-2")))
                .thenReturn(List.<Object[]>of(new Object[]{BUYER_ID, 3L, new BigDecimal("250.5")}));

        Page<AdminBuyerResponse> page = service.list(null, null, null, null, 0, 10);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).totalOrders()).isEqualTo(3L);
        assertThat(page.getContent().get(0).totalSpent()).isEqualByComparingTo("250.50");
        // A buyer who never ordered still needs a number in the column.
        assertThat(page.getContent().get(1).totalOrders()).isZero();
        assertThat(page.getContent().get(1).totalSpent()).isEqualByComparingTo("0.00");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userProfileRepository).findAll(
                ArgumentMatchers.<Specification<UserProfile>>any(), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void anEmptyPageAsksForNoOrderTotalsAtAll() {
        when(userProfileRepository.findAll(
                ArgumentMatchers.<Specification<UserProfile>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(service.list(null, null, null, null, 0, 10)).isEmpty();

        verify(purchaseRepository, never()).orderStatsForBuyers(anyString(), any());
    }

    @Test
    void summaryCountsEveryBuyerButOnlyCardsTheModeratedStandings() {
        when(userProfileRepository.countBuyersByStatus()).thenReturn(List.of(
                new Object[]{UserStatus.ACTIVE, 40L},
                new Object[]{UserStatus.SUSPENDED, 3L},
                new Object[]{UserStatus.BANNED, 2L},
                new Object[]{UserStatus.PENDING, 5L},
                // A legacy row with no standing recorded.
                new Object[]{null, 1L}));

        var summary = service.summary();

        assertThat(summary.total()).isEqualTo(51L);
        assertThat(summary.active()).isEqualTo(40L);
        assertThat(summary.suspended()).isEqualTo(3L);
        assertThat(summary.banned()).isEqualTo(2L);
    }

    @Test
    void banRecordsWhoDidItAndWhy() {
        UserProfile profile = buyer(BUYER_ID);
        when(userProfileRepository.findByIdForCommerceLock(BUYER_ID))
                .thenReturn(Optional.of(profile));
        when(userProfileRepository.save(profile)).thenReturn(profile);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var result = service.ban(BUYER_ID, new ModerateBuyerRequest("Fraudulent chargebacks"));

            assertThat(result.status()).isEqualTo(UserStatus.BANNED);
            assertThat(result.moderatedBy()).isEqualTo(ADMIN_ID);
            assertThat(result.moderatedAt()).isNotNull();
            assertThat(result.moderationReason()).isEqualTo("Fraudulent chargebacks");
        }
    }

    @Test
    void restoreClearsTheModerationTrail() {
        UserProfile profile = buyer(BUYER_ID);
        profile.setStatus(UserStatus.SUSPENDED);
        profile.setModeratedBy("admin-old");
        profile.setModeratedAt(LocalDateTime.parse("2026-08-01T09:00:00"));
        profile.setModerationReason("Old reason");
        when(userProfileRepository.findByIdForCommerceLock(BUYER_ID))
                .thenReturn(Optional.of(profile));
        when(userProfileRepository.save(profile)).thenReturn(profile);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var result = service.restore(BUYER_ID);

            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(result.moderatedBy()).isNull();
            assertThat(result.moderatedAt()).isNull();
            assertThat(result.moderationReason()).isNull();
        }
    }

    @Test
    void refusesToRestoreAnAccountThatWasNeverModerated() {
        when(userProfileRepository.findByIdForCommerceLock(BUYER_ID))
                .thenReturn(Optional.of(buyer(BUYER_ID)));

        assertThatThrownBy(() -> service.restore(BUYER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(userProfileRepository, never()).save(any());
    }

    /** Banning a shop owner here would leave their listings on sale. */
    @Test
    void refusesToModerateAnAccountThatOwnsAShop() {
        when(userProfileRepository.findByIdForCommerceLock(BUYER_ID))
                .thenReturn(Optional.of(buyer(BUYER_ID)));
        when(sellerRepository.existsById(BUYER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.ban(BUYER_ID, new ModerateBuyerRequest("Reason")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    var failure = (ResponseStatusException) error;
                    assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(failure.getReason()).contains("/api/v1/admin/sellers");
                });

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void searchMatchesNameEmailOrPhoneWithItsWildcardsEscaped() {
        CriteriaBuilder builder = applyFilter(null, "  50%_Off  ", null, null);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(builder, org.mockito.Mockito.times(3))
                .like(ArgumentMatchers.<Expression<String>>any(), pattern.capture(), eq('\\'));
        assertThat(pattern.getAllValues()).containsOnly("%50\\%\\_off%");
    }

    @Test
    void joinedToIncludesAccountsCreatedLaterThatSameDay() {
        CriteriaBuilder builder = applyFilter(
                null, null, null, LocalDate.parse("2026-08-20"));

        // Exclusive next-midnight bound, not 00:00 on the day itself.
        verify(builder).lessThan(any(), eq(LocalDate.parse("2026-08-21").atStartOfDay()));
    }

    @Test
    void noFiltersStillNarrowsToBuyersOnly() {
        CriteriaBuilder builder = applyFilter(null, "   ", null, null);

        verify(builder).not(any());
        verify(builder, never()).like(ArgumentMatchers.<Expression<String>>any(),
                anyString(), anyChar());
        verify(builder, never()).equal(any(), any(Object.class));
    }

    private CriteriaBuilder applyFilter(UserStatus status, String search,
                                        LocalDate joinedFrom, LocalDate joinedTo) {
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        specFor(status, search, joinedFrom, joinedTo).toPredicate(
                mock(Root.class, RETURNS_DEEP_STUBS),
                mock(CriteriaQuery.class, RETURNS_DEEP_STUBS),
                builder);
        return builder;
    }

    @SuppressWarnings("unchecked")
    private Specification<UserProfile> specFor(UserStatus status, String search,
                                               LocalDate joinedFrom, LocalDate joinedTo) {
        when(userProfileRepository.findAll(
                ArgumentMatchers.<Specification<UserProfile>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(status, search, joinedFrom, joinedTo, 0, 10);

        ArgumentCaptor<Specification<UserProfile>> spec =
                ArgumentCaptor.forClass(Specification.class);
        verify(userProfileRepository).findAll(spec.capture(), any(Pageable.class));
        return spec.getValue();
    }

    private static MockedStatic<AuthUtils> authenticatedAdmin() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(ADMIN_ID);
        return auth;
    }

    private static UserProfile buyer(String id) {
        UserProfile profile = new UserProfile(id);
        profile.setEmail(id + "@example.com");
        profile.setFullName("Buyer " + id);
        profile.setStatus(UserStatus.ACTIVE);
        return profile;
    }
}
