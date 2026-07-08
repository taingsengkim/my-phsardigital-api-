package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.UpdateAttributeRequest;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListingAttributeServiceImpl implements ListingAttributeService {

    private final ListingAttributeRepository listingAttributeRepository;
    private final ListingRepository listingRepository;
    private final ListingAttributeMapper listingAttributeMapper;



    @Override
    @Transactional
    public List<ListingAttributeResponse> addAttributes(UUID listingUuid, List<ListingAttributeCreateRequest> requests) {

        // 1. Check if listing exist
        Listing listing = listingRepository.findByUuidWithDetails(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        // 2. Check Authorization
        String currentSellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(currentSellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have no permission to add attribute");
        }

        // 3. Collect all existed key value.
        Set<String> existingKeys = listing.getListingAttributes().stream()
                .map(ListingAttribute::getKey)
                .collect(Collectors.toSet());


        // 4. Prepare a list to hold the new attribute
        List<ListingAttribute> attributes = new ArrayList<>();


        // 5. Determine the current maximum sort order among existing attributes.
        int currentMaxSort = listing.getListingAttributes().stream()
                .map(ListingAttribute::getSortOrder)
                .max(Integer::compareTo)
                .orElse(0);


        // 6. Iterate over each request in the batch.
        for (ListingAttributeCreateRequest req : requests) {

            // validate NO duplicate key
            if (existingKeys.contains(req.key())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Duplicate key: " + req.key());
            }
            existingKeys.add(req.key());

            ListingAttribute attr = listingAttributeMapper.fromCreateRequest(req);
            attr.setListing(listing);
            attr.setSortOrder(req.sortOrder() != null ? req.sortOrder() : ++currentMaxSort);
            attributes.add(attr);

        }

        List<ListingAttribute> saved = listingAttributeRepository.saveAll(attributes);
        return saved.stream()
                .map(listingAttributeMapper::toResponse)
                .collect(Collectors.toList());
    }


    @Override
    @Transactional
    public List<ListingAttributeResponse> updateAttributes(UUID listingUuid, List<UpdateAttributeRequest> updates) {

        //  Check if listing exist
        Listing listing = listingRepository.findByUuidWithDetails(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        //  Checking user Authorization
        String currentSellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(currentSellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }


        Map<UUID, ListingAttribute> attributeMap = listing.getListingAttributes().stream()
                .collect(Collectors.toMap(ListingAttribute::getUuid, Function.identity()));


        //  First, validate that all provided UUIDs exist in the listing
        for (UpdateAttributeRequest req : updates) {
            if (!attributeMap.containsKey(req.attributeUuid())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Attribute not found: " + req.attributeUuid());
            }
        }

        //  Collect existing keys
        Set<String> existingKeys = listing.getListingAttributes().stream()
                .map(ListingAttribute::getKey)
                .collect(Collectors.toSet());

        // Prepare a list to hold the attributes to be saved
        List<ListingAttribute> toUpdate = new ArrayList<>();

        // Iterate each update request
        for (UpdateAttributeRequest req : updates) {
            ListingAttribute attr = attributeMap.get(req.attributeUuid());

            //  Update key if provided
            if (req.newKey() != null && !req.newKey().equals(attr.getKey())) {
                // Check if the new key already exists on another attribute of this listing
                boolean keyExists = listing.getListingAttributes().stream()
                        .anyMatch(a -> a.getKey().equals(req.newKey()) && !a.getUuid().equals(attr.getUuid()));
                if (keyExists) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Key '" + req.newKey() + "' already exists on this listing.");
                }
                attr.setKey(req.newKey());
            }

            // Update value if provided
            if (req.newValue() != null) {
                attr.setValue(req.newValue());
            }

            //  Add to update list
            toUpdate.add(attr);
        }

        //  Save all updated attributes in one batch
        List<ListingAttribute> saved = listingAttributeRepository.saveAll(toUpdate);


        return saved.stream()
                .map(listingAttributeMapper::toResponse)
                .collect(Collectors.toList());
    }


    @Override
    @Transactional
    public void removeAttributes(UUID listingUuid, List<UUID> attributeUuids) {

        //  check if the listing exist
        Listing listing = listingRepository.findByUuidWithDetails(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        // check the authorization
        String currentSellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(currentSellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }


        Map<UUID, ListingAttribute> attributeMap = listing.getListingAttributes().stream()
                .collect(Collectors.toMap(ListingAttribute::getUuid, Function.identity()));

        // Validate that all provided UUIDs exist in the listing
        for (UUID uuid : attributeUuids) {
            if (!attributeMap.containsKey(uuid)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Attribute not found: " + uuid);
            }
        }

        // 5. Collect the attributes to delete
        List<ListingAttribute> toDelete = attributeUuids.stream()
                .map(attributeMap::get)
                .collect(Collectors.toList());

        // 6. Remove from the listing's collection
        listing.getListingAttributes().removeAll(toDelete);


        listingAttributeRepository.deleteAll(toDelete);
    }
}
