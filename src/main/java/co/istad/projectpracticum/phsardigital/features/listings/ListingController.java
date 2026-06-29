package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {
    private final  ListingService listingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ListingResponse createListing(@Valid @RequestBody ListingCreateRequest listingCreateRequest){
        return listingService.create(listingCreateRequest);
    }
    @GetMapping
    public Page<ListingResponse> getAll(@RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "0") Integer pageNumber,
                                        @RequestParam(defaultValue = "20") Integer pageSize) {
        if (status == null || status.isBlank()) {
            return listingService.getAll(pageNumber, pageSize); // public, ACTIVE-only
        }
        return listingService.getAllListingsByStatus(status, pageNumber, pageSize);
    }
    @GetMapping("/{uuid}")
    public ListingResponse getOne(@PathVariable UUID uuid) {
        return listingService.getListing(uuid);
    }
}
