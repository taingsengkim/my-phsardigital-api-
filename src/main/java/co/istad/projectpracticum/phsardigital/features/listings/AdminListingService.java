package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.AdminListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface AdminListingService {

    /**
     * The moderation table: every seller's listings in any status, newest first.
     *
     * <p>Unlike {@code GET /api/v1/listings?status=}, this carries the moderation trail
     * and can be filtered on more than one thing at a time.
     *
     * @param status   filters by status; null lists every status
     * @param sellerId filters to one shop; null lists every shop's
     * @param search   case-insensitive substring of the title
     */
    Page<AdminListingResponse> list(ListingStatus status, String sellerId, String search,
                                    int pageNumber, int pageSize);

    /** Takes a listing off the marketplace pending review. The seller cannot undo it. */
    ListingResponse suspend(UUID uuid, SuspendRequest request);

    /**
     * Takes a listing down for good. Reversible by an admin through {@link #restore},
     * but never by the seller, and it stops counting against their plan.
     */
    ListingResponse remove(UUID uuid, SuspendRequest request);

    /** Puts a suspended or removed listing back to whatever it was before. */
    ListingResponse restore(UUID uuid);

    /**
     * Erases the listing and its images outright.
     *
     * <p>For content that must not persist at all. Refused once any order names the
     * listing — {@link #remove} is the answer there, because an order line points at
     * the listing with a non-null key and deleting it would break the buyer's history.
     */
    void delete(UUID uuid);
}
