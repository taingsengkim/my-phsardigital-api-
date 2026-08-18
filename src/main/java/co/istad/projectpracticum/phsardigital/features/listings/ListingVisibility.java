package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who is allowed to see a listing.
 *
 * <p>Lifted out of {@code ListingServiceImpl.getListing} once a second endpoint needed
 * the same answer. A copied rule is a rule that drifts, and this is the one that decides
 * whether a suspended shop's products are still reachable — it should have exactly one
 * home.
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
     * Whether anybody at all may read this listing, the caller's token notwithstanding.
     *
     * <p>The shop has to be trading too, or a suspended seller's products stay reachable
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
