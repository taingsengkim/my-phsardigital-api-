package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.address.Address;
import co.istad.projectpracticum.phsardigital.features.address.AddressPhoto;
import co.istad.projectpracticum.phsardigital.features.address.AddressService;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.cart.Cart;
import co.istad.projectpracticum.phsardigital.features.cart.CartItem;
import co.istad.projectpracticum.phsardigital.features.cart.CartRepository;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingAvailability;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.CheckoutRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.stock.StockChannel;
import co.istad.projectpracticum.phsardigital.features.stock.StockLedger;
import co.istad.projectpracticum.phsardigital.features.stock.StockMovementReason;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private StockLedger stockLedger;

    @Test
    void confirmationRefusesASuspendedListingWithoutChangingInventory() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.SUSPENDED);
        listing.setStatusBeforeSuspension(ListingStatus.ACTIVE);
        Purchase purchase = pendingPurchase(purchaseUuid, listing);
        stubConfirmation(purchaseUuid, purchase, listing);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Listing is not available"))
                .when(listingAvailability).requireBuyable(listing);

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            assertThatThrownBy(() -> service().confirm(purchaseUuid))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Listing is not available");
        }

        assertThat(listing.getStockQty()).isEqualTo(1);
        assertThat(listing.getSold()).isZero();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
        assertThat(listing.getStatusBeforeSuspension()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PENDING);
        verify(listingRepository, never()).save(listing);
        verify(purchaseRepository, never()).save(purchase);
    }

    @Test
    void confirmationTakesTheOrderedQuantityThroughTheStockLedger() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.ACTIVE);
        Purchase purchase = pendingPurchase(purchaseUuid, listing);
        stubConfirmation(purchaseUuid, purchase, listing);

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            service().confirm(purchaseUuid);
        }

        // The arithmetic and the SOLD_OUT transition belong to StockLedger and are
        // covered there; what matters here is that confirm goes through it, under the
        // listing lock, attributed to this order.
        verify(stockLedger).apply(listing, -1, StockMovementReason.SALE,
                StockChannel.ONLINE, purchaseUuid, null);
        verify(entityManager).refresh(listing, LockModeType.PESSIMISTIC_WRITE);

        var order = inOrder(purchaseRepository, listingRepository);
        order.verify(purchaseRepository).findByUuidForUpdate(purchaseUuid);
        order.verify(listingRepository).findByUuidForUpdate(listing.getUuid());
    }

    @Test
    void retryingConfirmationReturnsCurrentOrderWithoutMutatingStockAgain() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.SOLD_OUT);
        listing.setStockQty(0);
        listing.setSold(1);
        Purchase purchase = purchase(purchaseUuid, listing, PurchaseStatus.CONFIRMED);
        when(purchaseRepository.findByUuidForUpdate(purchaseUuid)).thenReturn(Optional.of(purchase));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            service().confirm(purchaseUuid);
        }

        assertThat(listing.getStockQty()).isZero();
        assertThat(listing.getSold()).isEqualTo(1);
        verifyNoInteractions(listingRepository);
        verify(purchaseRepository, never()).save(purchase);
        verify(purchaseMapper).toResponse(purchase);
    }

    @Test
    void retryingCompletionReturnsCurrentOrderWithoutAnotherWrite() {
        UUID purchaseUuid = UUID.randomUUID();
        Purchase purchase = purchase(purchaseUuid, listing(ListingStatus.ACTIVE), PurchaseStatus.COMPLETED);
        when(purchaseRepository.findByUuidForUpdate(purchaseUuid)).thenReturn(Optional.of(purchase));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            service().complete(purchaseUuid);
        }

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.COMPLETED);
        verify(purchaseRepository, never()).save(purchase);
        verifyNoInteractions(listingRepository);
        verify(purchaseMapper).toResponse(purchase);
    }

    @Test
    void retryingConfirmationAfterCompletionDoesNotMutateInventory() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.SOLD_OUT);
        listing.setStockQty(0);
        listing.setSold(1);
        Purchase purchase = purchase(purchaseUuid, listing, PurchaseStatus.COMPLETED);
        when(purchaseRepository.findByUuidForUpdate(purchaseUuid)).thenReturn(Optional.of(purchase));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            service().confirm(purchaseUuid);
        }

        assertThat(listing.getStockQty()).isZero();
        assertThat(listing.getSold()).isEqualTo(1);
        verifyNoInteractions(listingRepository, sellerAccessGuard, listingAvailability);
        verify(purchaseRepository, never()).save(purchase);
    }

    @Test
    void retryingCancellationReturnsCurrentOrderWithoutRestoringStockAgain() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.ACTIVE);
        listing.setStockQty(1);
        listing.setSold(0);
        Purchase purchase = purchase(purchaseUuid, listing, PurchaseStatus.CANCELLED);
        purchase.setBuyerId("buyer-1");
        when(purchaseRepository.findByUuidForUpdate(purchaseUuid)).thenReturn(Optional.of(purchase));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            service().cancel(purchaseUuid);
        }

        assertThat(listing.getStockQty()).isEqualTo(1);
        assertThat(listing.getSold()).isZero();
        verifyNoInteractions(listingRepository);
        verify(purchaseMapper).toResponse(purchase);
    }

    @Test
    void buyerCannotCancelAfterSellerAcceptedTheOrder() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.ACTIVE);
        Purchase purchase = purchase(purchaseUuid, listing, PurchaseStatus.CONFIRMED);
        when(purchaseRepository.findByUuidForUpdate(purchaseUuid)).thenReturn(Optional.of(purchase));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            assertThatThrownBy(() -> service().cancel(purchaseUuid))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("already been accepted");
        }

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CONFIRMED);
        verifyNoInteractions(listingRepository);
    }

    @Test
    void sellerCancellationRestoresAcceptedInventoryExactlyOnce() {
        UUID purchaseUuid = UUID.randomUUID();
        Listing listing = listing(ListingStatus.SOLD_OUT);
        listing.setStockQty(0);
        listing.setSold(1);
        Purchase purchase = purchase(purchaseUuid, listing, PurchaseStatus.CONFIRMED);
        when(purchaseRepository.findByUuidForUpdate(purchaseUuid)).thenReturn(Optional.of(purchase));
        when(listingRepository.findByUuidForUpdate(listing.getUuid()))
                .thenReturn(Optional.of(listing));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            service().cancel(purchaseUuid);
        }

        verify(stockLedger).apply(listing, 1, StockMovementReason.CANCEL,
                StockChannel.ONLINE, purchaseUuid, null);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        verify(listingRepository).save(listing);
    }

    @Test
    void checkoutLocksAndConsumesTheCartOnce() {
        Cart cart = cartWithOneItem();
        // The key is the cart's own UUID: checkout refuses any other value.
        UUID cartUuid = cart.getUuid();
        when(userProfileRepository.findByIdForCommerceLock("buyer-1"))
                .thenReturn(Optional.of(buyerProfile()));
        when(purchaseRepository.findById(cartUuid)).thenReturn(Optional.empty());
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate("buyer-1", "seller-1"))
                .thenReturn(Optional.of(cart));
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            service().checkout("seller-1", new CheckoutRequest(
                    cartUuid,
                    null,
                    "  Phnom Penh  ",
                    "  Dara  ",
                    "  012345678  ",
                    "  Call before delivery  "));
        }

        verify(userProfileRepository).findByIdForCommerceLock("buyer-1");
        verify(cartRepository).findByBuyerIdAndSellerIdForUpdate("buyer-1", "seller-1");
        verify(cartRepository, never())
                .findByBuyerIdAndSellerProfile_SellerId("buyer-1", "seller-1");
        verify(cartRepository).delete(cart);
        ArgumentCaptor<Purchase> purchase = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(purchase.capture());
        assertThat(purchase.getValue().getUuid()).isEqualTo(cartUuid);
        assertThat(purchase.getValue().getShippingAddress()).isEqualTo("Phnom Penh");
        assertThat(purchase.getValue().getRecipientName()).isEqualTo("Dara");
        assertThat(purchase.getValue().getRecipientPhone()).isEqualTo("012345678");
        assertThat(purchase.getValue().getNote()).isEqualTo("Call before delivery");
        assertThat(purchase.getValue().getTotalPrice()).isEqualByComparingTo("10.00");
        assertThat(purchase.getValue().getItems()).hasSize(1);
    }

    @Test
    void checkoutCopiesTheAddressLandmarkPhotosOntoTheOrder() {
        Cart cart = cartWithOneItem();
        UUID cartUuid = cart.getUuid();
        UUID addressId = UUID.randomUUID();
        when(userProfileRepository.findByIdForCommerceLock("buyer-1"))
                .thenReturn(Optional.of(buyerProfile()));
        when(purchaseRepository.findById(cartUuid)).thenReturn(Optional.empty());
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate("buyer-1", "seller-1"))
                .thenReturn(Optional.of(cart));
        when(addressService.requireOwned(addressId, "buyer-1"))
                .thenReturn(addressWithPhotos(addressId));
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            service().checkout("seller-1",
                    new CheckoutRequest(cartUuid, addressId, null, null));
        }

        ArgumentCaptor<Purchase> purchase = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(purchase.capture());
        // Copied, not referenced: editing the address later must not rewrite the order.
        assertThat(purchase.getValue().getDeliveryPhotos()).hasSize(2);
        assertThat(purchase.getValue().getDeliveryPhotos())
                .extracting(PurchaseDeliveryPhoto::getCaption)
                .containsExactly("turn at the pagoda", "blue gate");
        assertThat(purchase.getValue().getDeliveryPhotos().getFirst().getPurchase())
                .isSameAs(purchase.getValue());
    }

    @Test
    void checkoutRejectsBuyingFromOwnShopBeforeTouchingTheCart() {
        UUID cartUuid = UUID.randomUUID();

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("seller-1");
            assertThatThrownBy(() -> service().checkout(
                    "seller-1",
                    new CheckoutRequest(cartUuid, null, "Phnom Penh", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN))
                    .hasMessageContaining("own shop");
        }

        verifyNoInteractions(cartRepository, purchaseRepository, sellerAccessGuard);
    }

    @Test
    void checkoutRejectsAmbiguousDeliveryAddressWithoutCreatingAnOrder() {
        UUID cartUuid = UUID.randomUUID();

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            assertThatThrownBy(() -> service().checkout(
                    "seller-1",
                    new CheckoutRequest(
                            cartUuid, UUID.randomUUID(), "Phnom Penh", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST))
                    .hasMessageContaining("not both");
        }

        // The delivery choice is validated before any row is read or locked.
        verifyNoInteractions(userProfileRepository, cartRepository, purchaseRepository);
    }

    @Test
    void retryingCheckoutReturnsTheOriginalOrderWithoutTouchingANewerCart() {
        UUID cartUuid = UUID.randomUUID();
        Purchase original = purchase(
                cartUuid, listing(ListingStatus.ACTIVE), PurchaseStatus.PENDING);
        original.setShippingAddress("Phnom Penh");
        when(userProfileRepository.findByIdForCommerceLock("buyer-1"))
                .thenReturn(Optional.of(buyerProfile()));
        when(purchaseRepository.findById(cartUuid)).thenReturn(Optional.of(original));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            service().checkout(
                    "seller-1",
                    new CheckoutRequest(cartUuid, null, "Phnom Penh", null));
        }

        verify(purchaseMapper).toResponse(original);
        verify(purchaseRepository, never()).save(any(Purchase.class));
        verifyNoInteractions(cartRepository, sellerAccessGuard, addressService, listingAvailability);
    }

    @Test
    void reusingACheckedOutCartWithDifferentDetailsIsRejected() {
        UUID cartUuid = UUID.randomUUID();
        Purchase original = purchase(
                cartUuid, listing(ListingStatus.ACTIVE), PurchaseStatus.PENDING);
        original.setShippingAddress("Phnom Penh");
        when(userProfileRepository.findByIdForCommerceLock("buyer-1"))
                .thenReturn(Optional.of(buyerProfile()));
        when(purchaseRepository.findById(cartUuid)).thenReturn(Optional.of(original));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            assertThatThrownBy(() -> service().checkout(
                    "seller-1",
                    new CheckoutRequest(cartUuid, null, "A different address", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT))
                    .hasMessageContaining("different checkout details");
        }

        verify(purchaseRepository, never()).save(any(Purchase.class));
        verifyNoInteractions(cartRepository, sellerAccessGuard);
    }

    @Test
    void checkoutRejectsMissingDeliveryContactBeforeCreatingTheOrder() {
        Cart cart = cartWithOneItem();
        UUID cartUuid = cart.getUuid();
        UserProfile buyerWithoutContact = new UserProfile("buyer-1");
        when(userProfileRepository.findByIdForCommerceLock("buyer-1"))
                .thenReturn(Optional.of(buyerWithoutContact));
        when(purchaseRepository.findById(cartUuid)).thenReturn(Optional.empty());
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate("buyer-1", "seller-1"))
                .thenReturn(Optional.of(cart));

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            assertThatThrownBy(() -> service().checkout(
                    "seller-1",
                    new CheckoutRequest(cartUuid, null, "Phnom Penh", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST))
                    .hasMessageContaining("recipient name and phone");
        }

        verify(purchaseRepository, never()).save(any(Purchase.class));
        verify(cartRepository, never()).delete(cart);
    }

    @Test
    void purchaseHistoryUsesNewestFirstStablePagination() {
        when(purchaseRepository.findByBuyerId(any(String.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        try (MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::extractUserId).thenReturn("buyer-1");
            service().findMyPurchases(null, 2, 25);
        }

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(purchaseRepository).findByBuyerId(any(String.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt"))
                .isNotNull()
                .extracting(order -> order.getDirection().name())
                .isEqualTo("DESC");
        assertThat(pageable.getValue().getSort().getOrderFor("uuid"))
                .isNotNull()
                .extracting(order -> order.getDirection().name())
                .isEqualTo("DESC");
    }

    private void stubConfirmation(UUID purchaseUuid, Purchase purchase, Listing listing) {
        when(purchaseRepository.findByUuidForUpdate(purchaseUuid)).thenReturn(Optional.of(purchase));
        when(listingRepository.findByUuidForUpdate(listing.getUuid()))
                .thenReturn(Optional.of(listing));
    }

    private PurchaseServiceImpl service() {
        return new PurchaseServiceImpl(
                purchaseRepository,
                cartRepository,
                listingRepository,
                purchaseMapper,
                sellerAccessGuard,
                addressService,
                listingAvailability,
                userProfileRepository,
                entityManager,
                stockLedger);
    }

    private static Listing listing(ListingStatus status) {
        Listing listing = new Listing();
        listing.setUuid(UUID.randomUUID());
        listing.setTitle("Phone");
        listing.setStockQty(1);
        listing.setSold(0);
        listing.setStatus(status);
        SellerProfile seller = new SellerProfile("seller-1");
        seller.setIsActive(true);
        listing.setSellerProfile(seller);
        return listing;
    }

    private static Purchase pendingPurchase(UUID uuid, Listing listing) {
        return purchase(uuid, listing, PurchaseStatus.PENDING);
    }

    private static Purchase purchase(UUID uuid, Listing listing, PurchaseStatus status) {
        SellerProfile seller = new SellerProfile("seller-1");
        Purchase purchase = new Purchase();
        purchase.setUuid(uuid);
        purchase.setBuyerId("buyer-1");
        purchase.setSellerProfile(seller);
        purchase.setStatus(status);

        PurchaseItem item = new PurchaseItem();
        item.setListing(listing);
        item.setPurchase(purchase);
        item.setQuantity(1);
        purchase.getItems().add(item);
        return purchase;
    }

    private static Cart cartWithOneItem() {
        Listing listing = listing(ListingStatus.ACTIVE);
        listing.setFullPrice(new BigDecimal("10.00"));

        Cart cart = new Cart();
        cart.setUuid(UUID.randomUUID());
        cart.setBuyerId("buyer-1");
        cart.setSellerProfile(listing.getSellerProfile());

        CartItem item = new CartItem();
        item.setUuid(UUID.randomUUID());
        item.setCart(cart);
        item.setListing(listing);
        item.setQuantity(1);
        cart.getItems().add(item);
        return cart;
    }

    private static Address addressWithPhotos(UUID addressId) {
        Address address = new Address();
        address.setId(addressId);
        address.setFormattedAddress("Borey Peng Huoth, 271, Phum 3, Boeng Keng Kang");
        address.setRecipient("Dara");
        address.setPhone("012345678");
        address.getPhotos().add(photo(address, "road.jpg", "turn at the pagoda", 0));
        address.getPhotos().add(photo(address, "gate.jpg", "blue gate", 1));
        return address;
    }

    private static AddressPhoto photo(Address address, String objectName,
                                      String caption, int sortOrder) {
        FileUpload file = new FileUpload();
        file.setId(UUID.randomUUID());
        file.setObjectName(objectName);

        AddressPhoto photo = new AddressPhoto();
        photo.setUuid(UUID.randomUUID());
        photo.setAddress(address);
        photo.setFile(file);
        photo.setCaption(caption);
        photo.setSortOrder(sortOrder);
        return photo;
    }

    private static UserProfile buyerProfile() {
        UserProfile buyer = new UserProfile("buyer-1");
        buyer.setFullName("Buyer One");
        buyer.setPhone("011111111");
        return buyer;
    }
}
