package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.CheckoutRequest;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    // ---> buyer
    @PostMapping("/checkout/{sellerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse checkout(@PathVariable String sellerId,
                                     @Valid @RequestBody CheckoutRequest request) {
        return purchaseService.checkout(sellerId, request);
    }

    @GetMapping
    public Page<PurchaseResponse> myPurchases(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {
        return purchaseService.findMyPurchases(pageNumber, pageSize);
    }

    @GetMapping("/{uuid}")
    public PurchaseResponse getPurchase(@PathVariable UUID uuid) {
        return purchaseService.findMyPurchaseByUuid(uuid);
    }

    // ---> seller
    @GetMapping("/seller/orders")
    public Page<PurchaseResponse> sellerOrders(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {
        return purchaseService.findSellerOrders(pageNumber, pageSize);
    }

    @PatchMapping("/{uuid}/confirm")
    public PurchaseResponse confirm(@PathVariable UUID uuid) {
        return purchaseService.confirm(uuid);
    }

    @PatchMapping("/{uuid}/complete")
    public PurchaseResponse complete(@PathVariable UUID uuid) {
        return purchaseService.complete(uuid);
    }

    @PatchMapping("/{uuid}/cancel")
    public PurchaseResponse cancel(@PathVariable UUID uuid) {
        return purchaseService.cancel(uuid);
    }
}