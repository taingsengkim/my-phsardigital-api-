package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.address.AddressService;
import co.istad.projectpracticum.phsardigital.features.cart.CartRepository;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingAvailability;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceImplTest {

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private PurchaseMapper purchaseMapper;
    @Mock
    private SellerAccessGuard sellerAccessGuard;
    @Mock
    private AddressService addressService;
    @Mock
    private ListingAvailability listingAvailability;

    @Test
    void confirmationDoesNotRemoveAListingSuspensionWhenStockReachesZero() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.SUSPENDED);
        listing.setStatusBeforeSuspension(ListingStatus.ACTIVE);
        Purchase purchase = pendingPurchase(purchaseUuid, listing);
        stubConfirmation(purchaseUuid, purchase, listing);

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            service().confirm(purchaseUuid);
        }

        assertThat(listing.getStockQty()).isZero();
        assertThat(listing.getSold()).isEqualTo(1);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
        assertThat(listing.getStatusBeforeSuspension()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CONFIRMED);
        verify(listingRepository).save(listing);
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void confirmationStillMarksAnActiveListingSoldOutWhenStockReachesZero() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.ACTIVE);
        Purchase purchase = pendingPurchase(purchaseUuid, listing);
        stubConfirmation(purchaseUuid, purchase, listing);

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            service().confirm(purchaseUuid);
        }

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD_OUT);
        assertThat(listing.getStockQty()).isZero();
    }

    private void stubConfirmation(UUID purchaseUuid, Purchase purchase, Listing listing) {
        when(purchaseRepository.findById(purchaseUuid)).thenReturn(Optional.of(purchase));
        when(listingRepository.findByUuidForUpdate(listing.getUuid()))
                .thenReturn(Optional.of(listing));
        when(purchaseRepository.save(purchase)).thenReturn(purchase);
    }

    private PurchaseServiceImpl service() {
        return new PurchaseServiceImpl(
                purchaseRepository,
                cartRepository,
                listingRepository,
                purchaseMapper,
                sellerAccessGuard,
                addressService,
                listingAvailability);
    }

    private static Listing listing(ListingStatus status) {
        Listing listing = new Listing();
        listing.setUuid(UUID.randomUUID());
        listing.setTitle("Phone");
        listing.setStockQty(1);
        listing.setSold(0);
        listing.setStatus(status);
        return listing;
    }

    private static Purchase pendingPurchase(UUID uuid, Listing listing) {
        SellerProfile seller = new SellerProfile("seller-1");
        Purchase purchase = new Purchase();
        purchase.setUuid(uuid);
        purchase.setSellerProfile(seller);
        purchase.setStatus(PurchaseStatus.PENDING);

        PurchaseItem item = new PurchaseItem();
        item.setListing(listing);
        item.setPurchase(purchase);
        item.setQuantity(1);
        purchase.getItems().add(item);
        return purchase;
    }
}
