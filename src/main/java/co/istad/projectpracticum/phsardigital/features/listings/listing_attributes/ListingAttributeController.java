package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;


import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.UpdateAttributeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listing_attributes")
@RequiredArgsConstructor
public class ListingAttributeController {


    private final ListingAttributeService listingAttributeService;


    @PostMapping("/{listingUuid}")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ListingAttributeResponse> addAttributes(
            @PathVariable UUID listingUuid,
            @Valid @RequestBody List<ListingAttributeCreateRequest> requests) {
        return listingAttributeService.addAttributes(listingUuid, requests);
    }


    @PatchMapping("/update/{listingUuid}")
    public List<ListingAttributeResponse> updateAttributes(
            @PathVariable UUID listingUuid,
            @Valid @RequestBody List<UpdateAttributeRequest> updates) {
        return listingAttributeService.updateAttributes(listingUuid, updates);
    }


    @DeleteMapping("/{listingUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAttributes(
            @PathVariable UUID listingUuid,
            @RequestBody List<UUID> attributeUuids) {
        listingAttributeService.removeAttributes(listingUuid, attributeUuids);
    }

}
