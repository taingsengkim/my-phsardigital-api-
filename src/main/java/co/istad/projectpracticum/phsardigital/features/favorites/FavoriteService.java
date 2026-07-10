package co.istad.projectpracticum.phsardigital.features.favorites;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface FavoriteService {

    /**
     * get products from favorites /  wishlist
     * @param pageable
     * author : Lor Vengroth
     */
    Page<ListingResponse> getFavorites(Pageable pageable);

    /**
     * add products to favorites / wishlist
     * @param listingUuid
     * author : Lor Vengroth
     */
    String addFavorite(UUID listingUuid);

    /**
     * remove multiple saved favorites at the same time
     * @param listingUuids
     * author : Lor Vengroth
     */
    void removeFavorites(List<UUID> listingUuids);
}
