package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Set;

/** The shared public-visibility and checkout-availability rule for a listing. */
@Component
@RequiredArgsConstructor
public class ListingAvailability {

    private static final Set<ListingStatus> PUBLIC_STATUSES =
            EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD_OUT);

    private final CategoryAvailability categoryAvailability;

    public boolean isPubliclyVisible(Listing listing) {
        return listing != null
                && PUBLIC_STATUSES.contains(listing.getStatus())
                && Boolean.TRUE.equals(listing.getSellerProfile().getIsActive())
                && categoryAvailability.isEffectivelyActive(listing.getCategory());
    }

    public boolean isBuyable(Listing listing) {
        return listing != null
                && listing.getStatus() == ListingStatus.ACTIVE
                && listing.getStockQty() != null
                && listing.getStockQty() > 0
                && Boolean.TRUE.equals(listing.getSellerProfile().getIsActive())
                && categoryAvailability.isEffectivelyActive(listing.getCategory());
    }

    public void requireBuyable(Listing listing) {
        if (!isBuyable(listing)) {
            String title = listing == null ? "listing" : listing.getTitle();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Listing is not available: " + title);
        }
    }
}
