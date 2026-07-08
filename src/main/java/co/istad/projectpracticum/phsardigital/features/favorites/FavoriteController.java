package co.istad.projectpracticum.phsardigital.features.favorites;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<ListingResponse> getFavorites(
            @PageableDefault(size = 10, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return favoriteService.getFavorites(pageable);
    }

    @PostMapping("/{listingUuid}")
    @ResponseStatus(HttpStatus.CREATED)
    public String addFavorite(@PathVariable UUID listingUuid) {
        return favoriteService.addFavorite(listingUuid);
    }


    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorites(@RequestBody List<UUID> listingUuids) {
        favoriteService.removeFavorites(listingUuids);
    }
}
