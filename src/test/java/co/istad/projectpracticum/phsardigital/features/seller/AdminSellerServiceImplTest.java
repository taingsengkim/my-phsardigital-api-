package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.seller.dto.AdminSellerResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSellerServiceImplTest {

    private static final String SELLER_ID = "seller-1";
    private static final String ADMIN_ID = "admin-1";

    @Mock
    private SellerRepository sellerRepository;
    @InjectMocks
    private AdminSellerServiceImpl service;

    @Test
    void suspendUsesTheLockedSellerLookup() {
        SellerProfile profile = activeSeller();
        when(sellerRepository.findByIdForUpdate(SELLER_ID)).thenReturn(Optional.of(profile));
        when(sellerRepository.save(profile)).thenReturn(profile);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var result = service.suspend(SELLER_ID, new SuspendRequest("Counterfeit products"));

            assertThat(result.isActive()).isFalse();
            assertThat(result.suspendedBy()).isEqualTo(ADMIN_ID);
            assertThat(result.suspendedAt()).isNotNull();
            assertThat(result.suspensionReason()).isEqualTo("Counterfeit products");
        }

        verify(sellerRepository).findByIdForUpdate(SELLER_ID);
        verify(sellerRepository, never()).findById(SELLER_ID);
        verify(sellerRepository).save(profile);
    }

    @Test
    void restoreUsesTheLockedSellerLookupAndClearsSuspensionDetails() {
        SellerProfile profile = activeSeller();
        profile.setIsActive(false);
        profile.setSuspendedBy("admin-old");
        profile.setSuspendedAt(java.time.LocalDateTime.now());
        profile.setSuspensionReason("Old reason");
        when(sellerRepository.findByIdForUpdate(SELLER_ID)).thenReturn(Optional.of(profile));
        when(sellerRepository.save(profile)).thenReturn(profile);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var result = service.restore(SELLER_ID);

            assertThat(result.isActive()).isTrue();
            assertThat(result.suspendedBy()).isNull();
            assertThat(result.suspendedAt()).isNull();
            assertThat(result.suspensionReason()).isNull();
        }

        verify(sellerRepository).findByIdForUpdate(SELLER_ID);
        verify(sellerRepository, never()).findById(SELLER_ID);
        verify(sellerRepository).save(profile);
    }

    @Test
    void missingShopFromTheLockedLookupReturnsNotFoundWithoutSaving() {
        when(sellerRepository.findByIdForUpdate(SELLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(SELLER_ID, new SuspendRequest("Reason")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(sellerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listsShopsNewestFirstAndCarriesTheModerationFields() {
        SellerProfile suspended = activeSeller();
        suspended.setIsActive(false);
        suspended.setSuspendedBy(ADMIN_ID);
        suspended.setSuspendedAt(LocalDateTime.parse("2026-08-20T10:15:00"));
        suspended.setSuspensionReason("Counterfeit products");
        when(sellerRepository.findAll(
                ArgumentMatchers.<Specification<SellerProfile>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(suspended)));

        Page<AdminSellerResponse> page = service.list(null, null, 0, 20);

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.sellerId()).isEqualTo(SELLER_ID);
            assertThat(row.isActive()).isFalse();
            assertThat(row.suspendedBy()).isEqualTo(ADMIN_ID);
            assertThat(row.suspensionReason()).isEqualTo("Counterfeit products");
        });

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sellerRepository).findAll(
                ArgumentMatchers.<Specification<SellerProfile>>any(), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    /**
     * The distinction the flag alone cannot make: an unapproved shop is inactive too,
     * and must not appear in the suspended queue.
     */
    @Test
    void suspendedFiltersOnTheRecordedSuspensionRatherThanTheActiveFlag() {
        CriteriaBuilder builder = applyFilter(AdminSellerStatus.SUSPENDED, null);

        verify(builder).isNotNull(any());
        verify(builder, never()).isFalse(any());
        verify(builder, never()).isTrue(any());
    }

    @Test
    void inactiveExcludesSuspendedShopsAndStillMatchesALegacyNullFlag() {
        Root<SellerProfile> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        specFor(AdminSellerStatus.INACTIVE, null)
                .toPredicate(root, mock(CriteriaQuery.class), builder);

        // false OR null, so a row written before the column had a default is still listed.
        verify(builder).isFalse(root.get("isActive"));
        verify(builder).isNull(root.get("isActive"));
        verify(builder).isNull(root.get("suspendedAt"));
    }

    @Test
    void searchIsLoweredAndItsWildcardsEscaped() {
        CriteriaBuilder builder = applyFilter(null, "  50%_Off  ");

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(builder).like(ArgumentMatchers.<Expression<String>>any(),
                pattern.capture(), eq('\\'));
        assertThat(pattern.getValue()).isEqualTo("%50\\%\\_off%");
    }

    @Test
    void neitherFilterNarrowsTheQuery() {
        CriteriaBuilder builder = applyFilter(null, "   ");

        verify(builder, never()).like(ArgumentMatchers.<Expression<String>>any(),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyChar());
        verify(builder, never()).isTrue(any());
        verify(builder, never()).isNotNull(any());
        // An empty conjunction — every shop.
        verify(builder).and(new jakarta.persistence.criteria.Predicate[0]);
    }

    /** Runs the service's specification against a mock builder and hands it back. */
    private CriteriaBuilder applyFilter(AdminSellerStatus status, String search) {
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        specFor(status, search).toPredicate(
                mock(Root.class, RETURNS_DEEP_STUBS), mock(CriteriaQuery.class), builder);
        return builder;
    }

    @SuppressWarnings("unchecked")
    private Specification<SellerProfile> specFor(AdminSellerStatus status, String search) {
        when(sellerRepository.findAll(
                ArgumentMatchers.<Specification<SellerProfile>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(status, search, 0, 20);

        ArgumentCaptor<Specification<SellerProfile>> spec =
                ArgumentCaptor.forClass(Specification.class);
        verify(sellerRepository).findAll(spec.capture(), any(Pageable.class));
        return spec.getValue();
    }

    private static MockedStatic<AuthUtils> authenticatedAdmin() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(ADMIN_ID);
        return auth;
    }

    private static SellerProfile activeSeller() {
        SellerProfile profile = new SellerProfile(SELLER_ID);
        profile.setBusinessName("Example Shop");
        profile.setIsActive(true);
        return profile;
    }
}
