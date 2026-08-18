package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.RelatedListingResponse;

import java.util.List;
import java.util.UUID;

public interface RelatedListingService {

    /**
     * Products worth showing next to this one: what buyers ordered in the same basket,
     * then the same category at a comparable price, then whatever else the shop sells.
     * A listing appears once, credited to the strongest reason that reached it.
     *
     * <p>Only {@code ACTIVE} listings from trading shops are suggested, so this returns
     * fewer than {@code limit} — or none — rather than padding the strip with products
     * that cannot be bought.
     *
     * @param uuid  the listing being viewed
     * @param limit how many suggestions to return; null for the default, clamped to a
     *              ceiling
     * @return the suggestions in display order, possibly empty
     * @throws org.springframework.web.server.ResponseStatusException with
     *         {@code 404 NOT_FOUND} when no listing has that UUID, or the caller may
     *         not see it
     */
    List<RelatedListingResponse> getRelated(UUID uuid, Integer limit);
}
