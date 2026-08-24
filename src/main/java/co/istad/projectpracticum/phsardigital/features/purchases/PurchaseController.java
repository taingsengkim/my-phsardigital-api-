package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.CheckoutRequest;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
@Validated
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
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater") int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100") int pageSize) {
        return purchaseService.findMyPurchases(status, pageNumber, pageSize);
    }

    @GetMapping("/{uuid}")
    public PurchaseResponse getPurchase(@PathVariable UUID uuid) {
        return purchaseService.findMyPurchaseByUuid(uuid);
    }

    // ---> seller
    @GetMapping("/seller/orders")
    public Page<PurchaseResponse> sellerOrders(
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater") int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100") int pageSize) {
        return purchaseService.findSellerOrders(status, pageNumber, pageSize);
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
