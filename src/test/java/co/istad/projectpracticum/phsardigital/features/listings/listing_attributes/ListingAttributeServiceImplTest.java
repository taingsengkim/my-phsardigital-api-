package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingAttributeServiceImplTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ListingAttributeMapper mapper;
    @Mock
    private ListingAttributeWriter writer;
    @Mock
    private SellerAccessGuard sellerAccessGuard;

    @Test
    void flushesNewRowsBeforeMappingTheirGeneratedUuids() {
        ListingAttributeServiceImpl service =
                new ListingAttributeServiceImpl(listingRepository, mapper, writer, sellerAccessGuard);
        UUID listingUuid = UUID.randomUUID();
        String sellerId = "seller-1";

        SellerProfile seller = new SellerProfile();
        seller.setSellerId(sellerId);
        Listing listing = new Listing();
        listing.setUuid(listingUuid);
        listing.setSellerProfile(seller);
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setListingAttributes(new ArrayList<>());

        ListingAttribute created = new ListingAttribute();
        created.setListing(listing);
        created.setKey("brand");
        created.setValue("Acme");

        ListingAttributeCreateRequest request =
                new ListingAttributeCreateRequest("brand", "Acme", null);
        when(listingRepository.findByUuidForEdit(listingUuid))
                .thenReturn(Optional.of(listing));
        when(writer.currentOf(listing)).thenReturn(new ArrayList<>());
        when(writer.apply(any(), any())).thenReturn(List.of(created));
        when(listingRepository.saveAndFlush(listing)).thenAnswer(invocation -> {
            created.setUuid(UUID.randomUUID());
            return listing;
        });

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn(sellerId);

            service.addAttributes(listingUuid, List.of(request));
        }

        assertThat(created.getUuid()).isNotNull();
        InOrder order = inOrder(writer, listingRepository, mapper);
        order.verify(writer).apply(any(), any());
        order.verify(listingRepository).saveAndFlush(listing);
        order.verify(mapper).toResponse(created);
        verify(sellerAccessGuard).requireActiveSeller(sellerId);
    }

    @Test
    void refusesAttributeEditsOnAnAdminSuspendedListing() {
        ListingAttributeServiceImpl service =
                new ListingAttributeServiceImpl(listingRepository, mapper, writer, sellerAccessGuard);
        UUID listingUuid = UUID.randomUUID();
        String sellerId = "seller-1";

        SellerProfile seller = new SellerProfile(sellerId);
        Listing listing = new Listing();
        listing.setSellerProfile(seller);
        listing.setStatus(ListingStatus.SUSPENDED);
        listing.setModerationReason("Counterfeit item");
        when(listingRepository.findByUuidForEdit(listingUuid)).thenReturn(Optional.of(listing));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn(sellerId);

            assertThatThrownBy(() -> service.removeAttributes(listingUuid, List.of(UUID.randomUUID())))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("Counterfeit item");
        }

        verifyNoInteractions(writer, sellerAccessGuard);
    }
}
