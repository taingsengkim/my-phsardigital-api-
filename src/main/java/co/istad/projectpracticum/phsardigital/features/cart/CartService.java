package co.istad.projectpracticum.phsardigital.features.cart;

import co.istad.projectpracticum.phsardigital.features.cart.dto.AddCartItemRequest;
import co.istad.projectpracticum.phsardigital.features.cart.dto.CartResponse;
import co.istad.projectpracticum.phsardigital.features.cart.dto.UpdateCartItemRequest;

import java.util.List;
import java.util.UUID;

public interface CartService {
    /** All of the current buyer's carts (one per shop). */
    List<CartResponse> getMyCarts();
    /** The current buyer's cart for one shop. */
    CartResponse getMyCartBySeller(String sellerId);
    /**
     * Add an item. The item's shop determines which cart it lands in;
     * a new cart is created for that shop if the buyer has none.
     */
    CartResponse addItem(AddCartItemRequest request);
    /** Change quantity of an item in the shop's cart. */
    CartResponse updateItem(String sellerId, UUID itemUuid, UpdateCartItemRequest request);
    /** Remove one item; deletes the cart if it becomes empty. */
    CartResponse removeItem(String sellerId, UUID itemUuid);
    /** Empty (delete) the buyer's cart for one shop. */
    void clear(String sellerId);
}