package co.istad.projectpracticum.phsardigital.features.seller;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SellerLockContractTest {

    @Test
    void sellerMutationLookupKeepsItsPessimisticWriteLock() throws Exception {
        Method method = SellerRepository.class.getMethod("findByIdForUpdate", String.class);

        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock)
                .as("%s must declare @Lock", method)
                .isNotNull();
        assertThat(lock.value())
                .as("%s lock mode", method)
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void sellerTradingLookupUsesASharedLockSoOrdersDoNotSerialise() throws Exception {
        Method method = SellerRepository.class.getMethod("findByIdForShare", String.class);

        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock)
                .as("%s must declare @Lock", method)
                .isNotNull();
        assertThat(lock.value())
                .as("%s lock mode", method)
                .isEqualTo(LockModeType.PESSIMISTIC_READ);
    }
}
