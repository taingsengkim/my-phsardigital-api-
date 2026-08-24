package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
