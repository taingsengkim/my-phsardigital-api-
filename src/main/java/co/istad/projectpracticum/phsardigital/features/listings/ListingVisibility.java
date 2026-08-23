package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who is allowed to see a listing. Shared by every route that reads one, so the rule
 * deciding whether a suspended shop's products stay reachable has a single home.
 */
@Component
@RequiredArgsConstructor
public class ListingVisibility {

    private final ListingAvailability listingAvailability;

    /** Whether the current caller — signed in or not — may read this listing at all. */
    public boolean isVisible(Listing listing) {
        return isPubliclyVisible(listing) || maySeePrivately(listing);
    }

    /**
     * The shop has to be trading too, or a suspended seller's products stay reachable
     * by direct link even though they are gone from search.
     */
    public boolean isPubliclyVisible(Listing listing) {
        return listingAvailability.isPubliclyVisible(listing);
    }

    /**
     * Whether the caller is the listing's own seller or an admin. Both checks are
     * anonymous-safe — {@code hasRole} answers false rather than throwing, and the
     * owner comparison is only reached for a caller who has a token.
     */
    private boolean maySeePrivately(Listing listing) {
        if (AuthUtils.hasRole("ADMIN")) {
            return true;
        }
        if (!AuthUtils.isAuthenticated()) {
            return false;
        }
        return listing.getSellerProfile().getSellerId().equals(AuthUtils.extractUserId());
    }
}
