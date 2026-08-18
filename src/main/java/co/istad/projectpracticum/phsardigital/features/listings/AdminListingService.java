package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;

import java.util.UUID;

public interface AdminListingService {

    /** Takes a listing off the marketplace. The seller cannot undo it. */
    ListingResponse suspend(UUID uuid, SuspendRequest request);

    /** Puts it back to whatever it was before the suspension. */
    ListingResponse restore(UUID uuid);
}
