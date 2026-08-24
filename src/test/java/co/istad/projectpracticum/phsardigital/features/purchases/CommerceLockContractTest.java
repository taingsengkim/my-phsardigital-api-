package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.cart.CartRepository;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceLockContractTest {

    @Test
    void everyAggregateMutationLookupKeepsItsPessimisticWriteLock() throws Exception {
        assertWriteLock(PurchaseRepository.class.getMethod(
                "findByUuidForUpdate", UUID.class));
        assertWriteLock(CartRepository.class.getMethod(
                "findByBuyerIdAndSellerIdForUpdate", String.class, String.class));
        assertWriteLock(UserProfileRepository.class.getMethod(
                "findByIdForCommerceLock", String.class));
    }

    private static void assertWriteLock(Method method) {
        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock)
                .as("%s must declare @Lock", method)
                .isNotNull();
        assertThat(lock.value())
                .as("%s lock mode", method)
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
