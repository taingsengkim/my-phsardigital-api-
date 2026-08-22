package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;


import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.UpdateAttributeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Editing one listing's specs after it is posted. Everything a seller sends here is put
 * past the category's schema first — see {@code ListingAttributeValidator} — so these
 * routes cannot be used to get around the checks {@code POST /api/v1/listings} makes.
 *
 * <p>The {@code @Valid} sits on the list element rather than the list, which is the only
 * form that reaches the records inside, and the reason the class carries {@code @Validated}.
 */
@RestController
@RequestMapping("/api/v1/listing_attributes")
@RequiredArgsConstructor
@Validated
public class ListingAttributeController {


    private final ListingAttributeService listingAttributeService;


    @PostMapping("/{listingUuid}")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ListingAttributeResponse> addAttributes(
            @PathVariable UUID listingUuid,
            @RequestBody @NotEmpty(message = "At least one attribute is required")
            List<@Valid ListingAttributeCreateRequest> requests) {
        return listingAttributeService.addAttributes(listingUuid, requests);
    }


    @PatchMapping("/update/{listingUuid}")
    public List<ListingAttributeResponse> updateAttributes(
            @PathVariable UUID listingUuid,
            @RequestBody @NotEmpty(message = "At least one update is required")
            List<@Valid UpdateAttributeRequest> updates) {
        return listingAttributeService.updateAttributes(listingUuid, updates);
    }


    @DeleteMapping("/{listingUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAttributes(
            @PathVariable UUID listingUuid,
            @RequestBody @NotEmpty(message = "At least one attribute uuid is required")
            List<UUID> attributeUuids) {
        listingAttributeService.removeAttributes(listingUuid, attributeUuids);
    }

}
