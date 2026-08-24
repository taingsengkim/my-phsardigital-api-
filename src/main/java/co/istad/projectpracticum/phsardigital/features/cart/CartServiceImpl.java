package co.istad.projectpracticum.phsardigital.features.cart;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.core.money.Money;
import co.istad.projectpracticum.phsardigital.features.cart.dto.*;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingAvailability;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileMapper;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ListingRepository listingRepository;
    private final ListingAvailability listingAvailability;
    private final SellerAccessGuard sellerAccessGuard;
    private final SellerProfileMapper sellerProfileMapper;
    private final FileUploadService fileUploadService;
    private final UserProfileRepository userProfileRepository;

    @Override
    public List<CartResponse> getMyCarts() {
        String buyerId = AuthUtils.extractUserId();
        List<CartResponse> result = new ArrayList<>();
        // An emptied cart is deleted rather than kept, so every row here has items.
        for (Cart cart : cartRepository.findByBuyerId(buyerId)) {
            result.add(toResponse(cart));
        }
        return result;
    }

    @Override
    public CartResponse getMyCartBySeller(String sellerId) {
        String buyerId = AuthUtils.extractUserId();
        Cart cart = cartRepository
                .findByBuyerIdAndSellerProfile_SellerId(buyerId, sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart for this shop."));
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        return writeItem(request, true);
    }

    @Override
    @Transactional
    public CartResponse setItem(AddCartItemRequest request) {
        return writeItem(request, false);
    }

    private CartResponse writeItem(AddCartItemRequest request, boolean increment) {
        String buyerId = AuthUtils.extractUserId();
        Listing listing = listingRepository.findById(request.listingUuid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
        listingAvailability.requireBuyable(listing);
        String sellerId = listing.getSellerProfile().getSellerId();
        // Checkout refuses a suspended shop anyway; refusing here too means the buyer
        // finds out before they have built a basket they cannot buy.
        sellerAccessGuard.requireActiveSeller(sellerId);
        // POST preserves the familiar "add more" behavior, so adding nothing is a
        // mistake worth naming. PUT supplies the desired total, where zero is the
        // ordinary way to say "no longer in my basket".
        if (increment && request.quantity() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quantity must be at least 1 when adding. Use PUT to set an exact total.");
        }
        Cart cart = lockedOrNewCart(buyerId, sellerId, listing);
        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getListing().getUuid().equals(listing.getUuid()))
                .findFirst()
                .orElse(null);

        int currentQty = existing == null ? 0 : existing.getQuantity();
        int newQty;
        try {
            newQty = increment
                    ? Math.addExact(currentQty, request.quantity())
                    : request.quantity();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cart quantity is too large.", exception);
        }
        // A desired total of zero removes the line, which is what a client retrying
        // its own "remove" naturally sends.
        if (newQty == 0) {
            if (existing != null) {
                cart.getItems().remove(existing);
            }
            return afterRemoval(cart);
        }
        if (listing.getStockQty() < newQty) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock for: " + listing.getTitle() + " (available " + listing.getStockQty() + ")");
        }
        if (existing != null) {
            existing.setQuantity(newQty);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setListing(listing);
            item.setQuantity(newQty);
            cart.getItems().add(item);
        }
        return toResponse(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public CartResponse updateItem(String sellerId, UUID itemUuid, UpdateCartItemRequest request) {
        Cart cart = getOwnedCart(sellerId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getUuid().equals(itemUuid))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart."));
        listingAvailability.requireBuyable(item.getListing());
        sellerAccessGuard.requireActiveSeller(item.getListing().getSellerProfile().getSellerId());
        if (item.getListing().getStockQty() < request.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock for: " + item.getListing().getTitle());
        }
        item.setQuantity(request.quantity());
        return toResponse(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public CartResponse removeItem(String sellerId, UUID itemUuid) {
        Optional<Cart> found = findOwnedCartForUpdate(sellerId);
        // Removing the last item deletes the cart, so a retried removal finds nothing.
        // That is the same outcome the caller asked for, not a 404.
        if (found.isEmpty()) {
            return new CartResponse(null, null, new ArrayList<>(), Money.ZERO);
        }
        Cart cart = found.get();
        cart.getItems().removeIf(i -> i.getUuid().equals(itemUuid));
        return afterRemoval(cart);
    }

    @Override
    @Transactional
    public void clear(String sellerId) {
        findOwnedCartForUpdate(sellerId).ifPresent(cartRepository::delete);
    }

    /**
     * An emptied cart is deleted so the shop slot is freed. The shop block is still
     * answered, so the page that emptied it can keep its heading. A cart that was
     * never persisted has nothing to delete.
     */
    private CartResponse afterRemoval(Cart cart) {
        if (cart.getItems().isEmpty()) {
            SellerProfileSummaryResponse shop = sellerProfileMapper.toSummary(cart.getSellerProfile());
            if (cart.getUuid() != null) {
                cartRepository.delete(cart);
            }
            return new CartResponse(null, shop, new ArrayList<>(), Money.ZERO);
        }
        return toResponse(cartRepository.save(cart));
    }

    private Cart getOwnedCart(String sellerId) {
        return findOwnedCartForUpdate(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart for this shop."));
    }

    private Optional<Cart> findOwnedCartForUpdate(String sellerId) {
        String buyerId = AuthUtils.extractUserId();
        return cartRepository.findByBuyerIdAndSellerIdForUpdate(buyerId, sellerId);
    }

    /**
     * Existing carts serialize on their own row. For a first cart there is no row to
     * lock, so briefly lock this buyer's profile and check again before creating it.
     * This avoids two simultaneous first adds racing the cart's buyer/shop constraint,
     * without making established carts for different shops block one another.
     */
    private Cart lockedOrNewCart(String buyerId, String sellerId, Listing listing) {
        return cartRepository.findByBuyerIdAndSellerIdForUpdate(buyerId, sellerId)
                .orElseGet(() -> {
                    userProfileRepository.findByIdForCommerceLock(buyerId)
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Buyer profile not found."));
                    return cartRepository.findByBuyerIdAndSellerIdForUpdate(buyerId, sellerId)
                            .orElseGet(() -> {
                                Cart cart = new Cart();
                                cart.setBuyerId(buyerId);
                                cart.setSellerProfile(listing.getSellerProfile());
                                return cart;
                            });
                });
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = Money.ZERO;
        for (CartItem it : cart.getItems()) {
            Listing listing = it.getListing();
            // The basket has to total what checkout will charge.
            BigDecimal unitPrice = listing.effectivePrice();
            BigDecimal line = Money.multiply(unitPrice, it.getQuantity());
            total = Money.add(total, line);
            items.add(new CartItemResponse(
                    it.getUuid(),
                    listing.getUuid(),
                    listing.getTitle(),
                    fileUploadService.getPreviewUrl(listing.getThumbnailFile()),
                    listing.getFullPrice(),
                    unitPrice,
                    it.getQuantity(),
                    line
            ));
        }
        return new CartResponse(cart.getUuid(),
                sellerProfileMapper.toSummary(cart.getSellerProfile()),
                items, total
        );
    }
}
