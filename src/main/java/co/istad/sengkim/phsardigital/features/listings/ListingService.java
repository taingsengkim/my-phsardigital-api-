package co.istad.sengkim.phsardigital.features.listings;

import co.istad.sengkim.phsardigital.features.listings.dto.ListingRequest;
import co.istad.sengkim.phsardigital.features.listings.dto.ListingResponse;
import co.istad.sengkim.phsardigital.features.listings.dto.UpdateListingRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface ListingService {

    Page<ListingResponse> findAll(int pageNumber, int pageSize);

    ListingResponse findByUuid(UUID uuid);

    ListingResponse findBySlug(String slug);

    List<ListingResponse> findByCategory(UUID categoryUuid);

    Page<ListingResponse> findBySeller(String sellerId, int pageNumber, int pageSize);

    ListingResponse createListing(ListingRequest listingRequest);

    ListingResponse updateListing(UUID uuid, UpdateListingRequest updateListingRequest);

    void softDeleteListing(UUID uuid);

    void hardDeleteListing(UUID uuid);

    ListingResponse setPrimaryImage(UUID listingUuid, UUID imageUuid);
}