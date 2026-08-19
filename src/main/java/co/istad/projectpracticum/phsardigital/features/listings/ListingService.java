package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingFilter;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.AddListingImageRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ListingService {
    /**
     * Retrieves a paginated list of every seller's listings in one status.
     *
     * <p>Admin-only — this is the moderation view. It was previously open to any
     * caller, which meant {@code ?status=DRAFT} published every seller's unfinished
     * work to anonymous visitors; sellers now use {@link #getMyListings} instead.
     *
     * @param status     the {@link ListingStatus} name to filter by (case-insensitive
     *                    string, e.g. {@code "ACTIVE"}, {@code "DRAFT"}); invalid
     *                    values result in a 400 Bad Request
     * @param pageNumber zero-based page index
     * @param pageSize   number of listings per page
     * @return a page of listings matching the given status
     * @throws org.springframework.web.server.ResponseStatusException with
     *         {@code 403 FORBIDDEN} when the caller is not an admin
     */
    Page<ListingResponse> getAllListingsByStatus(String status,Integer pageNumber, Integer pageSize);

    /**
     * Retrieves the calling seller's own listings, in any status.
     *
     * @param status     optional {@link ListingStatus} name to filter by; omit for all
     * @param pageNumber zero-based page index
     * @param pageSize   number of listings per page
     * @return a page of the caller's listings
     */
    Page<ListingResponse> getMyListings(String status, Integer pageNumber, Integer pageSize);
    /**
     * The public catalogue: browse, search, filter and sort.
     *
     * <p>Only {@code ACTIVE} listings from shops that are still trading are returned, so
     * this is safe for unauthenticated callers. Every filter is applied in the database.
     *
     * @param filter     what to narrow by; may be empty
     * @param pageNumber zero-based page index
     * @param pageSize   number of listings per page
     * @param sort       {@code field,direction} — see {@link ListingSort} for the
     *                   fields that can be sorted on; null for newest first
     * @return a page of active listings, each with its star rating attached
     * @throws org.springframework.web.server.ResponseStatusException with
     *         {@code 400 BAD_REQUEST} when {@code sort} names something unsortable
     */
    Page<ListingResponse> getAll(ListingFilter filter, Integer pageNumber, Integer pageSize, String sort);

    /**
     * Retrieves a single listing by the slug its public URL is built from.
     *
     * <p>Visibility is decided exactly as in {@link #getListing}.
     *
     * @param slug the listing's slug
     * @return the matching listing
     * @throws org.springframework.web.server.ResponseStatusException with
     *         {@code 404 NOT_FOUND} if no listing has that slug, or the caller may not
     *         see it
     */
    ListingResponse getListingBySlug(String slug);
    /**
     * Retrieves a single listing by its UUID.
     *
     * <p>{@code ACTIVE} and {@code SOLD_OUT} are public. Everything else — a draft, an
     * archived listing, one an admin has suspended — is visible only to its own seller
     * or to an admin, and answers 404 to anybody else: a suspended listing that still
     * loads from a direct link has not really been taken down.
     *
     * @param uuid the listing's unique identifier
     * @return the matching listing
     * @throws org.springframework.web.server.ResponseStatusException with
     *         {@code 404 NOT_FOUND} if no listing exists with the given UUID, or the
     *         caller may not see it
     */
    ListingResponse getListing(UUID uuid);

    /**
     * Creates a new listing.
     *
     * <p>The listing is always created with status {@code DRAFT} and
     * {@code soldCount = 0}, regardless of any value the client might send.
     * The seller is currently hardcoded to a test seller ID, since seller
     * authentication (Keycloak) has not yet been integrated.
     *
     * <p>Each entry in {@code request.images()} is attached as a new
     * {@code ListingImage} — there is no existing-image reconciliation here
     * since this is a create operation, not an update.
     *
     * @param request the listing data, including category, pricing, thumbnail
     *                object name, and optional gallery images
     * @return the created listing, with a freshly resolved presigned thumbnail
     *         URL and image URLs
     * @throws org.springframework.web.server.ResponseStatusException with
     *         {@code 404 NOT_FOUND} if {@code request.categoryUuid()} does not
     *         match an existing category
     */
    ListingResponse create(ListingCreateRequest request);

    /**
     * Updates the information of an existing listing.
     *
     * @param uuid the unique identifier of the listing
     * @param request the request containing the updated listing information
     * @return the updated listing
     */
    ListingResponse update(UUID uuid, UpdateListingRequest request);

    /**
     * Ends the sale on a listing, putting it back at its full price. Separate from
     * {@link #update}, where a null {@code discountPrice} means "leave it alone".
     *
     * @param uuid the unique identifier of the listing
     * @return the updated listing, its {@code discountPrice} now null
     */
    ListingResponse clearDiscount(UUID uuid);

    /**
     * Updates the thumbnail image of an existing listing.
     *
     * @param uuid the unique identifier of the listing
     * @param objectName the object name of the uploaded thumbnail image
     * @return the updated listing
     */
    ListingResponse updateThumbnail(UUID uuid, String objectName);
    /**
     * Adds a new image to an existing listing.
     *
     * @param uuid the unique identifier of the listing
     * @param addListingImageRequest the request containing the image information
     * @return the updated listing
     */
    ListingResponse addImage(UUID uuid, AddListingImageRequest addListingImageRequest);
    /**
     * Rearranges the listing's gallery.
     *
     * @param uuid the unique identifier of the listing
     * @param imageUuids every image on the listing, in the order they should appear
     * @return the updated listing, its images already in the new order
     * @throws org.springframework.web.server.ResponseStatusException with
     *         {@code 400 BAD_REQUEST} when the list does not name each of the
     *         listing's images exactly once
     */
    ListingResponse reorderImages(UUID uuid, List<UUID> imageUuids);

    /**
     * Removes an image from an existing listing.
     *
     * @param uuid the unique identifier of the listing
     * @param imageUuid the unique identifier of the image to be removed
     */
    void removeImage(UUID uuid, UUID imageUuid);


    /**
     delete a listing .
     * @param uuid the unique identifier to delete listing
     */
    void delete(UUID uuid);








}
