package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.UpdateAttributeRequest;

import java.util.List;
import java.util.UUID;

public interface ListingAttributeService {


    /**
     * adding attributes into listing
     * @param listingUuid
     * @param requests
     * author : Lor Vengroth
     */
    List<ListingAttributeResponse> addAttributes(UUID listingUuid, List<ListingAttributeCreateRequest> requests);


    /**
     * update attribute in listing
     * @param listingUuid
     * @param updates
     * author : Lor Vengroth
     */
    List<ListingAttributeResponse> updateAttributes(UUID listingUuid, List<UpdateAttributeRequest> updates);


    /**
     * delete multiple attribute at the same time
     * @param listingUuid
     * @param attributeUuids
     * author : Lor Vengroth
     */
    void removeAttributes(UUID listingUuid, List<UUID> attributeUuids);

}
