package co.istad.projectpracticum.phsardigital.features.cart;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.cart.dto.AddCartItemRequest;
import co.istad.projectpracticum.phsardigital.features.cart.dto.UpdateCartItemRequest;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingAvailability;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileMapper;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final String BUYER_ID = "buyer-1";
    private static final String SELLER_ID = "seller-1";

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ListingAvailability listingAvailability;
    @Mock
    private SellerAccessGuard sellerAccessGuard;
    @Mock
    private SellerProfileMapper sellerProfileMapper;
    @Mock
    private FileUploadService fileUploadService;
    @Mock
    private UserProfileRepository userProfileRepository;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(
                cartRepository,
                listingRepository,
                listingAvailability,
                sellerAccessGuard,
                sellerProfileMapper,
                fileUploadService,
                userProfileRepository);
    }

    @Test
    void addItemUsesLockedBuyerAndSellerLookup() {
        Listing listing = listing();
        when(listingRepository.findById(listing.getUuid())).thenReturn(Optional.of(listing));
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID))
                .thenReturn(Optional.<Cart>empty())
                .thenReturn(Optional.<Cart>empty());
        when(userProfileRepository.findByIdForCommerceLock(BUYER_ID))
                .thenReturn(Optional.of(new UserProfile(BUYER_ID)));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            cart.setUuid(UUID.randomUUID());
            return cart;
        });

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            var result = service.addItem(new AddCartItemRequest(listing.getUuid(), 2));

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().getFirst().quantity()).isEqualTo(2);
        }

        verify(cartRepository, times(2))
                .findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID);
        verify(userProfileRepository).findByIdForCommerceLock(BUYER_ID);
        verify(cartRepository, never())
                .findByBuyerIdAndSellerProfile_SellerId(BUYER_ID, SELLER_ID);
    }

    @Test
    void replayingAddToAnExistingCartSetsTheRequestedQuantityWithoutDoublingIt() {
        Cart cart = cartWithOneItem();
        Listing listing = cart.getItems().getFirst().getListing();
        when(listingRepository.findById(listing.getUuid())).thenReturn(Optional.of(listing));
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID))
                .thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            // setItem carries the desired total; addItem would increment 1 -> 3.
            var result = service.setItem(new AddCartItemRequest(listing.getUuid(), 2));

            assertThat(result.items().getFirst().quantity()).isEqualTo(2);
        }

        verify(cartRepository).findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID);
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void settingQuantityToZeroRemovesTheLineAndDeletesTheEmptiedCart() {
        Cart cart = cartWithOneItem();
        Listing listing = cart.getItems().getFirst().getListing();
        when(listingRepository.findById(listing.getUuid())).thenReturn(Optional.of(listing));
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID))
                .thenReturn(Optional.of(cart));

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            var result = service.setItem(new AddCartItemRequest(listing.getUuid(), 0));

            assertThat(result.items()).isEmpty();
        }

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).delete(cart);
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void addingZeroIsRejectedBecauseOnlyPutCanRemoveALine() {
        Listing listing = listing();
        when(listingRepository.findById(listing.getUuid())).thenReturn(Optional.of(listing));

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            assertThatThrownBy(() -> service.addItem(new AddCartItemRequest(listing.getUuid(), 0)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST))
                    .hasMessageContaining("at least 1");
        }

        verifyNoInteractions(cartRepository, userProfileRepository);
    }

    @Test
    void updateItemUsesLockedBuyerAndSellerLookup() {
        Cart cart = cartWithOneItem();
        CartItem item = cart.getItems().getFirst();
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID))
                .thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            service.updateItem(SELLER_ID, item.getUuid(), new UpdateCartItemRequest(3));
        }

        assertThat(item.getQuantity()).isEqualTo(3);
        verify(cartRepository).findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID);
    }

    @Test
    void removeItemUsesLockedBuyerAndSellerLookup() {
        Cart cart = cartWithOneItem();
        UUID itemUuid = cart.getItems().getFirst().getUuid();
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID))
                .thenReturn(Optional.of(cart));

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            service.removeItem(SELLER_ID, itemUuid);
        }

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID);
        verify(cartRepository).delete(cart);
    }

    @Test
    void retryingRemovalAfterTheCartWasDeletedIsASuccessfulNoOp() {
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID))
                .thenReturn(Optional.empty());

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            service.removeItem(SELLER_ID, UUID.randomUUID());
        }

        verify(cartRepository, never()).delete(any(Cart.class));
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void clearUsesLockedBuyerAndSellerLookup() {
        Cart cart = cartWithOneItem();
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID))
                .thenReturn(Optional.of(cart));

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            service.clear(SELLER_ID);
        }

        verify(cartRepository).findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID);
        verify(cartRepository).delete(cart);
    }

    @Test
    void retryingClearAfterTheCartWasDeletedIsASuccessfulNoOp() {
        when(cartRepository.findByBuyerIdAndSellerIdForUpdate(BUYER_ID, SELLER_ID))
                .thenReturn(Optional.empty());

        try (MockedStatic<AuthUtils> auth = authenticatedBuyer()) {
            service.clear(SELLER_ID);
        }

        verify(cartRepository, never()).delete(any(Cart.class));
    }

    private static MockedStatic<AuthUtils> authenticatedBuyer() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(BUYER_ID);
        return auth;
    }

    private static Cart cartWithOneItem() {
        Listing listing = listing();
        Cart cart = new Cart();
        cart.setUuid(UUID.randomUUID());
        cart.setBuyerId(BUYER_ID);
        cart.setSellerProfile(listing.getSellerProfile());

        CartItem item = new CartItem();
        item.setUuid(UUID.randomUUID());
        item.setCart(cart);
        item.setListing(listing);
        item.setQuantity(1);
        cart.getItems().add(item);
        return cart;
    }

    private static Listing listing() {
        SellerProfile seller = new SellerProfile(SELLER_ID);
        seller.setIsActive(true);

        Listing listing = new Listing();
        listing.setUuid(UUID.randomUUID());
        listing.setSellerProfile(seller);
        listing.setTitle("Phone");
        listing.setFullPrice(new BigDecimal("10.00"));
        listing.setStockQty(10);
        listing.setStatus(ListingStatus.ACTIVE);
        return listing;
    }
}
