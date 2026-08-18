package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.RelatedListingResponse;

import java.util.List;
import java.util.UUID;

public interface RelatedListingService {

    /**
     * Products worth showing next to this one.
     *
     * <p>Three signals, strongest first, each filling what the one before it left empty:
     * what buyers ordered in the same basket, then the same category at a comparable
     * price, then whatever else the shop sells. A listing appears once, credited to the
     * strongest reason that reached it, and the returned order is the order to render.
     *
     * <p>Only listings that can be bought are suggested — {@code ACTIVE}, from a shop
     * that is still trading. A strip that sends buyers to a sold-out or suspended
     * product is worse than a short strip, so this returns fewer than {@code limit},
     * or none at all, rather than padding.
     *
     * @param uuid  the listing being viewed
     * @param limit how many suggestions to return; null for the default, and clamped to
     *              a sane ceiling
     * @return the suggestions in display order, possibly empty
     * @throws org.springframework.web.server.ResponseStatusException with
     *         {@code 404 NOT_FOUND} when no listing has that UUID, or the caller may
     *         not see it
     */
    List<RelatedListingResponse> getRelated(UUID uuid, Integer limit);
}
