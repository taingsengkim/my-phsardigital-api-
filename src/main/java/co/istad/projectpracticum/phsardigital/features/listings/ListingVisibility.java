package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who is allowed to see a listing. Shared by every route that reads one, so the rule
 * deciding whether a suspended shop's products stay reachable has a single home.
 */
@Component
public class ListingVisibility {

    /** The statuses a listing is browsable in. Everything else is private to its shop. */
    private static final Set<ListingStatus> PUBLICLY_VISIBLE =
            EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD_OUT);

    /** Whether the current caller — signed in or not — may read this listing at all. */
    public boolean isVisible(Listing listing) {
        return isPubliclyVisible(listing) || maySeePrivately(listing);
    }

    /**
     * The shop has to be trading too, or a suspended seller's products stay reachable
     * by direct link even though they are gone from search.
     */
    public boolean isPubliclyVisible(Listing listing) {
        return PUBLICLY_VISIBLE.contains(listing.getStatus())
                && Boolean.TRUE.equals(listing.getSellerProfile().getIsActive());
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
