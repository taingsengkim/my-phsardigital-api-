package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.RelatedListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.AddListingImageRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ReorderImagesRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.UpdateThumbnailRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {
    private final  ListingService listingService;
    private final RelatedListingService relatedListingService;

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

    /**
     * The caller's own listings in any status. A literal segment, so it takes
     * precedence over {@code /{uuid}} below and "me" is never parsed as a UUID.
     */
    @GetMapping("/me")
    public Page<ListingResponse> getMine(@RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") Integer pageNumber,
                                         @RequestParam(defaultValue = "20") Integer pageSize) {
        return listingService.getMyListings(status, pageNumber, pageSize);
    }

    @GetMapping("/{uuid}")
    public ListingResponse getOne(@PathVariable UUID uuid) {
        return listingService.getListing(uuid);
    }

    /**
     * Products to show alongside this one. Public exactly as far as the listing itself
     * is — the service applies the same visibility rule before suggesting anything.
     */
    @GetMapping("/{uuid}/related")
    public List<RelatedListingResponse> getRelated(@PathVariable UUID uuid,
                                                   @RequestParam(required = false) Integer limit) {
        return relatedListingService.getRelated(uuid, limit);
    }

    @PatchMapping("/{uuid}")
    public ListingResponse update(@PathVariable UUID uuid, @Valid @RequestBody UpdateListingRequest request) {
        return listingService.update(uuid, request);
    }

    @PatchMapping("/{uuid}/thumbnail")
    public ListingResponse updateThumbnail(
            @PathVariable UUID uuid,
            @RequestBody @Valid UpdateThumbnailRequest request) {
        return listingService.updateThumbnail(uuid, request.objectName());
    }

    @PostMapping("/{uuid}/images")
    public ListingResponse addImage(@PathVariable UUID uuid,
                                    @RequestBody AddListingImageRequest addListingImageRequest) {
        return listingService.addImage(uuid, addListingImageRequest);
    }

    /**
     * Rearranges the gallery. Takes the whole order at once rather than one image's
     * position, so the gallery never sits in a state the seller did not ask for.
     */
    @PatchMapping("/{uuid}/images/order")
    public ListingResponse reorderImages(@PathVariable UUID uuid,
                                         @Valid @RequestBody ReorderImagesRequest request) {
        return listingService.reorderImages(uuid, request.imageUuids());
    }

    @DeleteMapping("/{uuid}/images/{imageUuid}")
    public void removeImage(@PathVariable UUID uuid, @PathVariable UUID imageUuid) {
        listingService.removeImage(uuid, imageUuid);
    }


    @DeleteMapping("/{uuid}")
    public void remove(@PathVariable UUID uuid){
        listingService.delete(uuid);
    }


}
