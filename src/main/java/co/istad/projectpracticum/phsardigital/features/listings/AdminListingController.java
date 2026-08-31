package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.AdminListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Product moderation.
 *
 * <p>Three levels, deliberately distinct: {@code suspend} is a hold pending review,
 * {@code remove} is a permanent takedown that stops costing the seller a plan slot,
 * and {@code DELETE} erases the row — refused once any order names the listing.
 */
@RestController
@RequestMapping("/api/v1/admin/listings")
@RequiredArgsConstructor
@Validated
public class AdminListingController {

    private final AdminListingService adminListingService;

    /**
     * The moderation table. Carries the moderation trail, which the public
     * {@code ListingResponse} deliberately does not.
     */
    @GetMapping
    public Page<AdminListingResponse> list(
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false)
            @Size(max = 120, message = "Search must not exceed 120 characters")
            String search,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize) {
        return adminListingService.list(status, sellerId, search, pageNumber, pageSize);
    }

    @PatchMapping("/{uuid}/suspend")
    public ListingResponse suspend(@PathVariable UUID uuid,
                                   @Valid @RequestBody SuspendRequest request) {
        return adminListingService.suspend(uuid, request);
    }

    @PatchMapping("/{uuid}/remove")
    public ListingResponse remove(@PathVariable UUID uuid,
                                  @Valid @RequestBody SuspendRequest request) {
        return adminListingService.remove(uuid, request);
    }

    @PatchMapping("/{uuid}/restore")
    public ListingResponse restore(@PathVariable UUID uuid) {
        return adminListingService.restore(uuid);
    }

    /** Erases the listing. Use {@code /remove} for anything that has ever been ordered. */
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        adminListingService.delete(uuid);
    }
}
