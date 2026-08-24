package co.istad.projectpracticum.phsardigital.features.seller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerAccessGuardTest {

    private static final String SELLER_ID = "seller-1";

    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private EntityManager entityManager;
    @InjectMocks
    private SellerAccessGuard guard;

    @Test
    void updateGuardLocksAndRefreshesBeforeCheckingWhetherTheShopIsActive() {
        SellerProfile profile = activeSeller();
        when(sellerRepository.findByIdForShare(SELLER_ID)).thenReturn(Optional.of(profile));
        doAnswer(invocation -> {
            profile.setIsActive(false);
            profile.setSuspensionReason("Policy violation");
            return null;
        }).when(entityManager).refresh(profile, LockModeType.PESSIMISTIC_READ);

        assertThatThrownBy(() -> guard.requireActiveSellerForTrade(SELLER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("Policy violation");

        InOrder order = inOrder(sellerRepository, entityManager);
        order.verify(sellerRepository).findByIdForShare(SELLER_ID);
        order.verify(entityManager).refresh(profile, LockModeType.PESSIMISTIC_READ);
        verify(sellerRepository, never()).findById(SELLER_ID);
    }

    @Test
    void updateGuardReturnsTheCurrentActiveProfileWhileHoldingTheSharedLock() {
        SellerProfile profile = activeSeller();
        when(sellerRepository.findByIdForShare(SELLER_ID)).thenReturn(Optional.of(profile));

        SellerProfile result = guard.requireActiveSellerForTrade(SELLER_ID);

        assertThat(result).isSameAs(profile);
        verify(entityManager).refresh(profile, LockModeType.PESSIMISTIC_READ);
    }

    @Test
    void updateGuardDoesNotRefreshWhenTheSellerProfileDoesNotExist() {
        when(sellerRepository.findByIdForShare(SELLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireActiveSellerForTrade(SELLER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(entityManager);
    }

    private static SellerProfile activeSeller() {
        SellerProfile profile = new SellerProfile(SELLER_ID);
        profile.setIsActive(true);
        return profile;
    }
}
