package co.istad.projectpracticum.phsardigital.features.seller;


import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerProfileController {

    private final SellerProfileService service;

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
    public SellerProfileResponse updateMyProfile(@RequestBody SellerProfileUpdateRequest request) {
        return service.updateMyProfile(request);
    }
}
