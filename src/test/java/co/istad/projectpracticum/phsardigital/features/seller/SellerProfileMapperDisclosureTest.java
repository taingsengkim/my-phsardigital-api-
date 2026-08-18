package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Which routes may repeat a moderation decision back to a caller: the reason a shop was
 * suspended goes to its seller and to admins, and to nobody else. That distinction
 * lives entirely in which mapper method a route picks, so it is worth a test.
 */
class SellerProfileMapperDisclosureTest {

    private SellerProfileMapper mapper;
    private SellerProfile suspended;

    @BeforeEach
    void setUp() {
        mapper = new SellerProfileMapperImpl();
        mapper.fileUploadService = mock(FileUploadService.class);

        suspended = new SellerProfile("seller-1");
        suspended.setBusinessName("Phsar Thmey Handicrafts");
        suspended.setIsActive(false);
        suspended.setSuspendedBy("admin-1");
        suspended.setSuspendedAt(LocalDateTime.of(2026, 8, 1, 9, 30));
        suspended.setSuspensionReason("Counterfeit goods reported by three buyers.");
    }

    @Test
    void theOwnerIsToldWhyTheirShopWasSuspended() {
        SellerProfileResponse response = mapper.toOwnerResponse(suspended, 4.5, 12L);

        assertThat(response.isActive()).isFalse();
        assertThat(response.suspensionReason()).isEqualTo("Counterfeit goods reported by three buyers.");
        assertThat(response.suspendedAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 30));
    }

    @Test
    void thePublicProfileWithholdsTheReason() {
        SellerProfileResponse response = mapper.toResponseWithRating(suspended, 4.5, 12L);

        // Still says the shop cannot trade — just not why.
        assertThat(response.isActive()).isFalse();
        assertThat(response.suspensionReason()).isNull();
        assertThat(response.suspendedAt()).isNull();
    }

    /** The method MapStruct reaches for when a shop block is embedded in a review. */
    @Test
    void theEmbeddedShopBlockWithholdsTheReason() {
        SellerProfileResponse response = mapper.toResponse(suspended);

        assertThat(response.suspensionReason()).isNull();
        assertThat(response.suspendedAt()).isNull();
    }
}
