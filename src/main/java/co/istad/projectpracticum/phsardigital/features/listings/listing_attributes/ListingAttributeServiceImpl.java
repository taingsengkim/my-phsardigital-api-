package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeResolver;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingAttributeResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.UpdateAttributeRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every operation here ends up handing {@link ListingAttributeWriter} the complete set the
 * listing should end up with, rather than writing its own rows. A spec cannot be checked
 * on its own: whether "8 GB" is a legal answer depends on the category, and whether the
 * listing is still valid after a delete depends on what is left.
 */
@Service
@RequiredArgsConstructor
public class ListingAttributeServiceImpl implements ListingAttributeService {

    private final ListingRepository listingRepository;
    private final ListingAttributeMapper listingAttributeMapper;
    private final ListingAttributeWriter listingAttributeWriter;
    private final SellerAccessGuard sellerAccessGuard;

    @Override
    @Transactional
    public List<ListingAttributeResponse> addAttributes(UUID listingUuid,
                                                        List<ListingAttributeCreateRequest> requests) {
        Listing listing = editableListing(listingUuid, "You have no permission to add attribute");

        List<ListingAttributeCreateRequest> desired = listingAttributeWriter.currentOf(listing);
        Set<String> existingKeys = desired.stream()
                .map(request -> CategoryAttributeResolver.normaliseKey(request.key()))
                .collect(Collectors.toCollection(HashSet::new));

        int nextSortOrder = desired.stream()
                .map(ListingAttributeCreateRequest::sortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;

        for (ListingAttributeCreateRequest request : requests) {
            // Rejected here rather than left to the writer, which would report it as a
            // duplicate within one set — true, but not the useful half of the story when
            // the clash is with something already on the listing.
            if (!existingKeys.add(CategoryAttributeResolver.normaliseKey(request.key()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This listing already has an attribute for '" + request.key() + "'.");
            }
            desired.add(new ListingAttributeCreateRequest(
                    request.key(),
                    request.value(),
                    request.sortOrder() != null ? request.sortOrder() : nextSortOrder++));
        }

        return respondAfterFlush(listing, listingAttributeWriter.apply(listing, desired));
    }

    @Override
    @Transactional
    public List<ListingAttributeResponse> updateAttributes(UUID listingUuid,
                                                           List<UpdateAttributeRequest> updates) {
        Listing listing = editableListing(listingUuid, "Not allowed");

        Map<UUID, ListingAttribute> byUuid = listing.getListingAttributes().stream()
                .collect(Collectors.toMap(ListingAttribute::getUuid, Function.identity()));

        // The set is rebuilt from the listing's current order, so an update touching one
        // attribute leaves the rest exactly where they were.
        Map<UUID, ListingAttributeCreateRequest> desired = new LinkedHashMap<>();
        for (ListingAttribute attribute : listing.getListingAttributes()) {
            desired.put(attribute.getUuid(), new ListingAttributeCreateRequest(
                    attribute.getKey(), attribute.getValue(), attribute.getSortOrder()));
        }

        for (UpdateAttributeRequest update : updates) {
            ListingAttribute attribute = byUuid.get(update.attributeUuid());
            if (attribute == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Attribute not found: " + update.attributeUuid());
            }
            ListingAttributeCreateRequest current = desired.get(update.attributeUuid());
            desired.put(update.attributeUuid(), new ListingAttributeCreateRequest(
                    update.newKey() != null ? update.newKey() : current.key(),
                    update.newValue() != null ? update.newValue() : current.value(),
                    current.sortOrder()));
        }

        return respondAfterFlush(listing,
                listingAttributeWriter.apply(listing, new ArrayList<>(desired.values())));
    }

    @Override
    @Transactional
    public void removeAttributes(UUID listingUuid, List<UUID> attributeUuids) {
        Listing listing = editableListing(listingUuid, "Not allowed");

        Set<UUID> present = listing.getListingAttributes().stream()
                .map(ListingAttribute::getUuid)
                .collect(Collectors.toSet());
        for (UUID uuid : attributeUuids) {
            if (!present.contains(uuid)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attribute not found: " + uuid);
            }
        }

        Set<UUID> removing = new HashSet<>(attributeUuids);
        List<ListingAttributeCreateRequest> remaining = listing.getListingAttributes().stream()
                .filter(attribute -> !removing.contains(attribute.getUuid()))
                .map(attribute -> new ListingAttributeCreateRequest(
                        attribute.getKey(), attribute.getValue(), attribute.getSortOrder()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Goes through the writer rather than deleting directly, so removing the spec the
        // category insists on is refused instead of quietly leaving the listing invalid.
        listingAttributeWriter.apply(listing, remaining);
    }

    /** The listing, if it exists and the caller is the seller who owns it. */
    private Listing editableListing(UUID listingUuid, String forbiddenMessage) {
        // Attribute endpoints replace a complete set. Locking the listing prevents two
        // concurrent PATCH/DELETE requests from validating the same old set and then
        // silently overwriting one another.
        Listing listing = listingRepository.findByUuidForEdit(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        String sellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        if (listing.getStatus() == ListingStatus.SUSPENDED
                || listing.getStatus() == ListingStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This listing has been taken down by an administrator and its attributes "
                            + "cannot be changed. Reason: " + listing.getModerationReason());
        }
        sellerAccessGuard.requireActiveSeller(sellerId);
        return listing;
    }

    private List<ListingAttributeResponse> respond(List<ListingAttribute> attributes) {
        return attributes.stream().map(listingAttributeMapper::toResponse).toList();
    }

    /**
     * New attribute UUIDs are assigned during persistence. Flush before mapping so a
     * successful POST/PATCH never responds with a null UUID for a row it just created.
     */
    private List<ListingAttributeResponse> respondAfterFlush(
            Listing listing, List<ListingAttribute> attributes) {
        listingRepository.saveAndFlush(listing);
        return respond(attributes);
    }
}
