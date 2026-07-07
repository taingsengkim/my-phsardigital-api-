package co.istad.projectpracticum.phsardigital.features.cart;
import co.istad.projectpracticum.phsardigital.features.cart.dto.AddCartItemRequest;
import co.istad.projectpracticum.phsardigital.features.cart.dto.CartResponse;
import co.istad.projectpracticum.phsardigital.features.cart.dto.UpdateCartItemRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;
    // all carts (one per shop)
    @GetMapping
    public List<CartResponse> myCarts() {
        return cartService.getMyCarts();
    }
    // one shop's cart
    @GetMapping("/{sellerId}")
    public CartResponse myCartBySeller(@PathVariable String sellerId) {
        return cartService.getMyCartBySeller(sellerId);
    }

    // add item (shop inferred from the listing)
    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(request);
    }

    @PatchMapping("/{sellerId}/items/{itemUuid}")
    public CartResponse updateItem(@PathVariable String sellerId,
                                   @PathVariable UUID itemUuid,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(sellerId, itemUuid, request);
    }

    @DeleteMapping("/{sellerId}/items/{itemUuid}")
    public CartResponse removeItem(@PathVariable String sellerId,
                                   @PathVariable UUID itemUuid) {
        return cartService.removeItem(sellerId, itemUuid);
    }

    // clear one shop's cart
    @DeleteMapping("/{sellerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String sellerId) {
        cartService.clear(sellerId);
    }
}