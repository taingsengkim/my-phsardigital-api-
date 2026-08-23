package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeWriter;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminListingServiceImplTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ListingMapper listingMapper;
    @Mock
    private CategoryAvailability categoryAvailability;
    @Mock
    private ListingAttributeWriter listingAttributeWriter;

    @Test
    void restoresPublicListingUnderLockAfterAllAvailabilityAndSchemaChecks() {
        AdminListingServiceImpl service = service();
        UUID listingUuid = UUID.randomUUID();
        Listing listing = suspendedListing(listingUuid, ListingStatus.ACTIVE, 4);
        List<ListingAttributeCreateRequest> current = List.of(
                new ListingAttributeCreateRequest("brand", "Acme", 0));

        when(listingRepository.findByUuidForEdit(listingUuid)).thenReturn(Optional.of(listing));
        when(categoryAvailability.isEffectivelyActive(listing.getCategory())).thenReturn(true);
        when(listingAttributeWriter.currentOf(listing)).thenReturn(current);
        when(listingRepository.save(listing)).thenReturn(listing);

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("admin-1");
            service.restore(listingUuid);
        }

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.getStatusBeforeSuspension()).isNull();
        assertThat(listing.getModerationReason()).isNull();
        assertThat(listing.getModeratedBy()).isEqualTo("admin-1");

        InOrder order = inOrder(categoryAvailability, listingAttributeWriter, listingRepository);
        order.verify(categoryAvailability).isEffectivelyActive(listing.getCategory());
        order.verify(listingAttributeWriter).currentOf(listing);
        order.verify(listingAttributeWriter).apply(listing, current);
        order.verify(listingRepository).save(listing);
        verify(listingRepository, never()).findByUuidWithDetails(listingUuid);
    }

    @Test
    void downgradesAFormerlyActiveZeroStockListingToSoldOut() {
        AdminListingServiceImpl service = service();
        UUID listingUuid = UUID.randomUUID();
        Listing listing = suspendedListing(listingUuid, ListingStatus.ACTIVE, 0);

        when(listingRepository.findByUuidForEdit(listingUuid)).thenReturn(Optional.of(listing));
        when(categoryAvailability.isEffectivelyActive(listing.getCategory())).thenReturn(true);
        when(listingAttributeWriter.currentOf(listing)).thenReturn(List.of());
        when(listingRepository.save(listing)).thenReturn(listing);

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("admin-1");
            service.restore(listingUuid);
        }

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD_OUT);
        verify(listingAttributeWriter).apply(listing, List.of());
    }

    @Test
    void refusesPublicRestoreWhileTheOwningShopIsInactive() {
        AdminListingServiceImpl service = service();
        UUID listingUuid = UUID.randomUUID();
        Listing listing = suspendedListing(listingUuid, ListingStatus.ACTIVE, 2);
        listing.getSellerProfile().setIsActive(false);

        when(listingRepository.findByUuidForEdit(listingUuid)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.restore(listingUuid))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("shop is inactive");

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
        verifyNoInteractions(categoryAvailability, listingAttributeWriter, listingMapper);
        verify(listingRepository, never()).save(listing);
    }

    @Test
    void refusesPublicRestoreWhenCategoryIsNotEffectivelyActive() {
        AdminListingServiceImpl service = service();
        UUID listingUuid = UUID.randomUUID();
        Listing listing = suspendedListing(listingUuid, ListingStatus.SOLD_OUT, 0);

        when(listingRepository.findByUuidForEdit(listingUuid)).thenReturn(Optional.of(listing));
        when(categoryAvailability.isEffectivelyActive(listing.getCategory())).thenReturn(false);

        assertThatThrownBy(() -> service.restore(listingUuid))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("category");

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
        assertThat(listing.getStatusBeforeSuspension()).isEqualTo(ListingStatus.SOLD_OUT);
        verifyNoInteractions(listingAttributeWriter, listingMapper);
        verify(listingRepository, never()).save(listing);
    }

    @Test
    void rejectsSchemaInvalidPublicRestoreWithoutChangingModerationState() {
        AdminListingServiceImpl service = service();
        UUID listingUuid = UUID.randomUUID();
        Listing listing = suspendedListing(listingUuid, ListingStatus.ACTIVE, 2);
        List<ListingAttributeCreateRequest> current = List.of();

        when(listingRepository.findByUuidForEdit(listingUuid)).thenReturn(Optional.of(listing));
        when(categoryAvailability.isEffectivelyActive(listing.getCategory())).thenReturn(true);
        when(listingAttributeWriter.currentOf(listing)).thenReturn(current);
        when(listingAttributeWriter.apply(listing, current)).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brand is required."));

        assertThatThrownBy(() -> service.restore(listingUuid))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("current category schema")
                .hasMessageContaining("Brand is required");

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
        assertThat(listing.getStatusBeforeSuspension()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.getModerationReason()).isEqualTo("Needs review");
        verify(listingRepository, never()).save(listing);
        verifyNoInteractions(listingMapper);
    }

    @Test
    void archivedRestoreDoesNotApplyPublicCatalogueRules() {
        AdminListingServiceImpl service = service();
        UUID listingUuid = UUID.randomUUID();
        Listing listing = suspendedListing(listingUuid, ListingStatus.ARCHIVED, 0);

        when(listingRepository.findByUuidForEdit(listingUuid)).thenReturn(Optional.of(listing));
        when(listingRepository.save(listing)).thenReturn(listing);

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("admin-1");
            service.restore(listingUuid);
        }

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ARCHIVED);
        verifyNoInteractions(categoryAvailability, listingAttributeWriter);
    }

    private AdminListingServiceImpl service() {
        return new AdminListingServiceImpl(
                listingRepository,
                listingMapper,
                categoryAvailability,
                listingAttributeWriter);
    }

    private static Listing suspendedListing(
            UUID uuid, ListingStatus statusBeforeSuspension, int stockQty) {
        SellerProfile seller = new SellerProfile("seller-1");
        seller.setIsActive(true);

        Category category = new Category();
        category.setUuid(UUID.randomUUID());
        category.setIsActive(true);
        category.setIsDeleted(false);

        Listing listing = new Listing();
        listing.setUuid(uuid);
        listing.setSellerProfile(seller);
        listing.setCategory(category);
        listing.setStatus(ListingStatus.SUSPENDED);
        listing.setStatusBeforeSuspension(statusBeforeSuspension);
        listing.setStockQty(stockQty);
        listing.setModerationReason("Needs review");
        listing.setListingAttributes(new ArrayList<>());
        return listing;
    }
}
