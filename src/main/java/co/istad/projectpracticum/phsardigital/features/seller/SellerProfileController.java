package co.istad.projectpracticum.phsardigital.features.seller;


import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import co.istad.projectpracticum.phsardigital.features.seller.dto.TopSellerResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
@Validated
public class SellerProfileController {

    private final SellerProfileService service;
    private final TopSellerService topSellerService;

    /**
     * The Top Sellers board. Declared before {@code /{sellerId}} for readability —
     * a literal segment outranks a variable one regardless.
     */
    @GetMapping("/top")
    public List<TopSellerResponse> topSellers(
            @RequestParam(defaultValue = "REVENUE") TopSellerBasis basis,
            @RequestParam(defaultValue = "LAST_30_DAYS") TopSellerPeriod period,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit must not exceed 50")
            int limit) {
        return topSellerService.getTopSellers(basis, period, limit);
    }

    @GetMapping("/{sellerId}")
    public SellerProfileResponse getPublicProfile(@PathVariable String sellerId) {
        return service.getPublicProfile(sellerId);
    }

    @GetMapping("/{sellerId}/listings")
    public Page<ListingResponse> getSellerListings(
            @PathVariable String sellerId,
            @PageableDefault(size = 10) Pageable pageable) {
        return service.getSellerListings(sellerId, pageable);
    }

    @GetMapping("/me")
    public SellerProfileResponse getMyProfile() {
        return service.getMyProfile();
    }

    @PatchMapping("/me")
    public SellerProfileResponse updateMyProfile(@Valid @RequestBody SellerProfileUpdateRequest request) {
        return service.updateMyProfile(request);
    }
}
