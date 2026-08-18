package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/listings")
@RequiredArgsConstructor
public class AdminListingController {

    private final AdminListingService adminListingService;

    @PatchMapping("/{uuid}/suspend")
    public ListingResponse suspend(@PathVariable UUID uuid,
                                   @Valid @RequestBody SuspendRequest request) {
        return adminListingService.suspend(uuid, request);
    }

    @PatchMapping("/{uuid}/restore")
    public ListingResponse restore(@PathVariable UUID uuid) {
        return adminListingService.restore(uuid);
    }
}
