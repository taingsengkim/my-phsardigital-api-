package co.istad.projectpracticum.phsardigital.features.cart;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.cart.dto.*;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ListingRepository listingRepository;

    @Override
    public List<CartResponse> getMyCarts() {
        String buyerId = AuthUtils.extractUserId();
        List<CartResponse> result = new ArrayList<>();
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
        String buyerId = AuthUtils.extractUserId();
        Listing listing = listingRepository.findById(request.listingUuid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Listing is not available: " + listing.getTitle());
        }
        String sellerId = listing.getSellerProfile().getSellerId();
        // find this buyer's cart for THIS shop, or create one
        Cart cart = cartRepository
                .findByBuyerIdAndSellerProfile_SellerId(buyerId, sellerId)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setBuyerId(buyerId);
                    c.setSellerProfile(listing.getSellerProfile());
                    return c;
                });
        // already in this cart? bump quantity : new line
        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getListing().getUuid().equals(listing.getUuid()))
                .findFirst()
                .orElse(null);

        int newQty = (existing == null ? 0 : existing.getQuantity()) + request.quantity();
        if (listing.getStockQty() < newQty) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock for: " + listing.getTitle() + " (available " + listing.getStockQty() + ")");
        }
        if (existing != null) {
            existing.setQuantity(newQty);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setListing(listing);
            item.setQuantity(request.quantity());
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
        if (item.getListing().getStockQty() < request.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock for: " + item.getListing().getTitle());
        }
        item.setQuantity(request.quantity());
        return toResponse(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public CartResponse removeItem(String sellerId, UUID itemUuid) {
        Cart cart = getOwnedCart(sellerId);
        cart.getItems().removeIf(i -> i.getUuid().equals(itemUuid));
        // empty cart -> delete it so the shop slot is freed
        if (cart.getItems().isEmpty()) {
            cartRepository.delete(cart);
            return new CartResponse(null, sellerId, new ArrayList<>(), 0.0);
        }
        return toResponse(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public void clear(String sellerId) {
        Cart cart = getOwnedCart(sellerId);
        cartRepository.delete(cart);
    }

    private Cart getOwnedCart(String sellerId) {
        String buyerId = AuthUtils.extractUserId();
        return cartRepository.findByBuyerIdAndSellerProfile_SellerId(buyerId, sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart for this shop."));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        double total = 0.0;
        for (CartItem it : cart.getItems()) {
            double line = it.getListing().getPrice() * it.getQuantity();
            total += line;
            items.add(new CartItemResponse(
                    it.getUuid(),
                    it.getListing().getUuid(),
                    it.getListing().getTitle(),
                    it.getListing().getPrice(),
                    it.getQuantity(),
                    line
            ));
        }
        return new CartResponse(cart.getUuid(),
                cart.getSellerProfile().getSellerId(),
                items, total
        );
    }
}