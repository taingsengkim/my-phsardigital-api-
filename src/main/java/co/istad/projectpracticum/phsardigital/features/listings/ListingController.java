package co.istad.projectpracticum.phsardigital.features.listings;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.features.listings.dto.AttributeFilter;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingFilter;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.RelatedListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.AddListingImageRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ReorderImagesRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.UpdateThumbnailRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
@Validated
public class ListingController {
    private final  ListingService listingService;
    private final RelatedListingService relatedListingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ListingResponse createListing(@Valid @RequestBody ListingCreateRequest listingCreateRequest){
        return listingService.create(listingCreateRequest);
    }

    /**
     * The public catalogue. {@code status} is the exception to the filters below and
     * stays admin-only — it is the moderation view, on its own unfiltered path.
     *
     * @param sort {@code field,direction}, e.g. {@code price,asc}
     * @param attr repeated {@code key:value} facets drawn from the category's attribute
     *             schema, e.g. {@code ?attr=ram:8 GB&attr=panel:AMOLED}. Different keys
     *             narrow the search together; repeats of one key widen it. The schema a
     *             storefront draws its facet panel from is at
     *             {@code GET /api/v1/categories/slug/{slug}/attributes}
     */
    @GetMapping
    public Page<ListingResponse> getAll(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) UUID categoryUuid,
                                        @RequestParam(required = false) String categorySlug,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false) String sellerId,
                                        @RequestParam(required = false) BigDecimal minPrice,
                                        @RequestParam(required = false) BigDecimal maxPrice,
                                        @RequestParam(required = false) List<String> attr,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(defaultValue = "0")
                                        @Min(value = 0, message = "Page number must be zero or greater")
                                        Integer pageNumber,
                                        @RequestParam(defaultValue = "20")
                                        @Min(value = 1, message = "Page size must be at least 1")
                                        @Max(value = 100, message = "Page size must not exceed 100")
                                        Integer pageSize) {
        if (status == null || status.isBlank()) {
            ListingFilter filter = new ListingFilter(
                    categoryUuid, categorySlug, search, sellerId, minPrice, maxPrice,
                    AttributeFilter.parse(attr));
            return listingService.getAll(filter, pageNumber, pageSize, sort); // public, ACTIVE-only
        }
        return listingService.getAllListingsByStatus(status, pageNumber, pageSize);
    }

    /**
     * A listing by the slug its public URL is built from, mirroring
     * {@code /api/v1/categories/slug/{slug}}. A literal segment, so it takes precedence
     * over {@code /{uuid}} and a slug is never parsed as a UUID.
     */
    @GetMapping("/slug/{slug}")
    public ListingResponse getBySlug(@PathVariable String slug) {
        return listingService.getListingBySlug(slug);
    }

    /**
     * The caller's own listings in any status. A literal segment, so it takes
     * precedence over {@code /{uuid}} below and "me" is never parsed as a UUID.
     *
     * @param search matches the product's title or its shop code, which is what a
     *               counter needs when an item is scanned or half-typed
     */
    @GetMapping("/me")
    public Page<ListingResponse> getMine(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(defaultValue = "0")
                                         @Min(value = 0, message = "Page number must be zero or greater")
                                         Integer pageNumber,
                                         @RequestParam(defaultValue = "20")
                                         @Min(value = 1, message = "Page size must be at least 1")
                                         @Max(value = 100, message = "Page size must not exceed 100")
                                         Integer pageSize) {
        return listingService.getMyListings(status, search, pageNumber, pageSize);
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

    /** Ends the sale. Its own route because PATCH reads a null field as "unchanged". */
    @DeleteMapping("/{uuid}/discount")
    public ListingResponse clearDiscount(@PathVariable UUID uuid) {
        return listingService.clearDiscount(uuid);
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
