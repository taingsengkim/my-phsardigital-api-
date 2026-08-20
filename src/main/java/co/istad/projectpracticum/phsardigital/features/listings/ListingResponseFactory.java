package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.favorites.FavoriteRepository;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds {@link ListingResponse}s with their star rating attached, fetching the
 * aggregate once per page rather than once per card.
 */
@Component
@RequiredArgsConstructor
public class ListingResponseFactory {

    private final ListingMapper listingMapper;
    private final ReviewRepository reviewRepository;
    private final FavoriteRepository favoriteRepository;

    public ListingResponse one(Listing listing) {
        return listingMapper.toResponse(listing).withRating(
                round(reviewRepository.averageRatingForListing(listing.getUuid())),
                reviewRepository.countByListing_Uuid(listing.getUuid()),
                favouritedAmong(List.of(listing.getUuid())).contains(listing.getUuid()));
    }

    public Page<ListingResponse> page(Page<Listing> listings) {
        Map<UUID, Rating> ratings = ratingsFor(listings.getContent());
        Set<UUID> favourited = favouritedAmong(uuidsOf(listings.getContent()));
        return listings.map(listing -> attach(listing, ratings, favourited));
    }

    public List<ListingResponse> all(List<Listing> listings) {
        Map<UUID, Rating> ratings = ratingsFor(listings);
        Set<UUID> favourited = favouritedAmong(uuidsOf(listings));
        return listings.stream().map(listing -> attach(listing, ratings, favourited)).toList();
    }

    private ListingResponse attach(Listing listing, Map<UUID, Rating> ratings, Set<UUID> favourited) {
        // Absent means nobody has reviewed it: no average at all, and a count of zero.
        Rating rating = ratings.get(listing.getUuid());
        return listingMapper.toResponse(listing).withRating(
                rating == null ? null : round(rating.average()),
                rating == null ? 0L : rating.count(),
                favourited.contains(listing.getUuid()));
    }

    /**
     * Which of these the caller has saved. Empty for an anonymous visitor rather than a
     * failed lookup — the catalogue is public, and asking who is browsing must not be
     * what turns a page into a 403.
     */
    private Set<UUID> favouritedAmong(List<UUID> uuids) {
        if (uuids.isEmpty() || !AuthUtils.isAuthenticated()) {
            return Set.of();
        }
        return Set.copyOf(favoriteRepository.findFavouritedUuids(AuthUtils.extractUserId(), uuids));
    }

    private static List<UUID> uuidsOf(List<Listing> listings) {
        return listings.stream().map(Listing::getUuid).toList();
    }

    private Map<UUID, Rating> ratingsFor(List<Listing> listings) {
        if (listings.isEmpty()) {
            return Map.of();
        }
        List<UUID> uuids = listings.stream().map(Listing::getUuid).toList();
        Map<UUID, Rating> ratings = new HashMap<>();
        for (Object[] row : reviewRepository.ratingsForListings(uuids)) {
            ratings.put((UUID) row[0],
                    new Rating(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return ratings;
    }

    /** Rounded here so every route answers the same 4.3, not 4.333333333333333. */
    private static Double round(Double average) {
        if (average == null) {
            return null;
        }
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private record Rating(double average, long count) {
    }
}
