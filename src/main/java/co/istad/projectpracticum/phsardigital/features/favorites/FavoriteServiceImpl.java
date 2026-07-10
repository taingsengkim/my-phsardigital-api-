package co.istad.projectpracticum.phsardigital.features.favorites;


import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingMapper;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserProfileRepository userProfileRepository;
    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;

    @Override
    public Page<ListingResponse> getFavorites(Pageable pageable) {

        // 1. Get current authenticated user
        UserProfile userProfile = userProfileRepository.findById(AuthUtils.extractUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // 2. Fetch paginated favorites
        Page<Favorite> favoritesPage = favoriteRepository.findByUserProfile(userProfile, pageable);

        // 3. Map each Favorite to ListingResponse
        return favoritesPage.map(favorite -> {
            Listing listing = favorite.getListing();
            return listingMapper.toResponse(listing);

        });
    }

    @Override
    @Transactional
    public String addFavorite(UUID listingUuid) {

        // 1. Get current user
        UserProfile userProfile = userProfileRepository.findById(AuthUtils.extractUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // 2. Check if listing exists and is active
        Listing listing = listingRepository.findById(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Listing not found"));


        // 3. Check duplicate
        if (favoriteRepository.existsByUserProfileAndListingUuid(userProfile, listingUuid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Listing already in favorites");


        }

        // 4. Save new favorite
        Favorite favorite = new Favorite();
        favorite.setUserProfile(userProfile);
        favorite.setListing(listing);
        favoriteRepository.save(favorite);
        return "List save to favorite successfully";
    }

    @Override
    @Transactional
    public void removeFavorites(List<UUID> listingUuids) {

        // 1. Get current user
        String currentUserId = AuthUtils.extractUserId();
        UserProfile userProfile = userProfileRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 2. Fetch all favorites for this user and the given listing UUIDs
        List<Favorite> favorites = favoriteRepository.findAllByUserProfileAndListingUuidIn(userProfile, listingUuids);

        // 3. Optional: check if all were found
         if (favorites.size() != listingUuids.size()) {
             throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Some favorites not found");
         }

        // 4. Delete all found favorites
        favoriteRepository.deleteAll(favorites);
    }
}
