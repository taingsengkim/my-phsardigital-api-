package co.istad.projectpracticum.phsardigital.features.seller;


import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.NearbySellerResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import co.istad.projectpracticum.phsardigital.features.seller.dto.TopSellerResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    private final NearbySellerService nearbySellerService;

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

    /**
     * Shops near the caller, closest first — the coordinates come from the device, so
     * they are parameters rather than anything the server knows about whoever is asking.
     *
     * <p>The radius is capped: without a ceiling, "near me" over a whole country is a
     * full table scan the bounding box cannot narrow, and the answer would not be a
     * neighbourhood any more.
     */
    @GetMapping("/nearby")
    public List<NearbySellerResponse> nearbySellers(
            @RequestParam
            @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
            double latitude,
            @RequestParam
            @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
            double longitude,
            @RequestParam(defaultValue = "10")
            @DecimalMin(value = "0.1", message = "Radius must be at least 0.1 km")
            @DecimalMax(value = "100.0", message = "Radius must not exceed 100 km")
            double radiusKm,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit must not exceed 50")
            int limit) {
        return nearbySellerService.findNearby(latitude, longitude, radiusKm, limit);
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
