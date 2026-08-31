package co.istad.projectpracticum.phsardigital.features.listings;

import java.math.BigDecimal;
import co.istad.projectpracticum.phsardigital.config.config.Utils;
import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.dto.AttributeFilter;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingFilter;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeWriter;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.ListingImage;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.AddListingImageRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ListingImageRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.stock.StockChannel;
import co.istad.projectpracticum.phsardigital.features.stock.StockLedger;
import co.istad.projectpracticum.phsardigital.features.stock.StockMovementReason;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingServiceImpl implements ListingService{
    private final ListingRepository listingRepository;
    private final CategoryRepository categoryRepository;
    private final SellerAccessGuard sellerAccessGuard;
    private final SubscriptionService subscriptionService;
    private final FileUploadService fileUploadService;
    private final ListingVisibility listingVisibility;
    private final ListingResponseFactory listingResponseFactory;
    private final ListingAttributeWriter listingAttributeWriter;
    private final CategoryAvailability categoryAvailability;
    private final ListingFacetValidator listingFacetValidator;
    private final StockLedger stockLedger;

    /**
     * The moderation view: every seller's listings in one status.
     *
     * <p>Admin-only, and checked here rather than in {@code SecurityConfig} because
     * the restriction is on a query parameter, not a path — the URL is the same public
     * {@code /api/v1/listings} either way. Without this the endpoint answered
     * {@code ?status=DRAFT} to anonymous callers, publishing every seller's unfinished
     * work; sellers reach their own through {@link #getMyListings} instead.
     */
    @Override
    public Page<ListingResponse> getAllListingsByStatus(String status, Integer pageNumber, Integer pageSize) {
        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Filtering listings by status is an administrator view. "
                            + "Use GET /api/v1/listings/me for your own listings.");
        }
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC,("lastModifiedAt")));
        Page<Listing> listingPage = listingRepository.findByStatus(parseStatus(status), pageable);
        return listingResponseFactory.page(listingPage);
    }

    /**
     * The caller's own listings, whatever state they are in — the legitimate half of
     * what {@code ?status=} used to serve.
     */
    @Override
    public Page<ListingResponse> getMyListings(String status, Integer pageNumber, Integer pageSize) {
        String sellerId = AuthUtils.extractUserId();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC,("lastModifiedAt")));

        Page<Listing> listingPage = (status == null || status.isBlank())
                ? listingRepository.findBySellerProfile_SellerId(sellerId, pageable)
                : listingRepository.findBySellerProfile_SellerIdAndStatus(sellerId, parseStatus(status), pageable);
        return listingResponseFactory.page(listingPage);
    }

    private ListingStatus parseStatus(String status) {
        try {
            return ListingStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid listing status: " + status);
        }
    }

    @Override
    @Transactional
    public Page<ListingResponse> getAll(ListingFilter filter, Integer pageNumber, Integer pageSize, String sort) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, ListingSort.parse(sort));

        Specification<Listing> spec = ListingSpecifications.publiclyBrowsable();
        Set<UUID> publiclyAvailableCategories = categoryRepository
                .findAllByIsDeletedFalseAndIsActiveTrue(Sort.unsorted())
                .stream()
                .filter(categoryAvailability::isEffectivelyActive)
                .map(Category::getUuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (publiclyAvailableCategories.isEmpty()) {
            return Page.empty(pageable);
        }
        // Listing status and seller state are not enough: a deactivated ancestor hides
        // the whole branch. Resolving effective category availability up front keeps
        // even legacy inconsistent trees out without breaking page totals.
        spec = spec.and(ListingSpecifications.inCategories(publiclyAvailableCategories));

        if (filter != null) {
            boolean categoryRequested = hasCategoryFilter(filter);
            Optional<Category> rootCategory = resolveCategory(filter);
            if (categoryRequested && rootCategory.isEmpty()) {
                // Named a category that is missing or unavailable: nothing public
                // matches it, and the response does not disclose which case it was.
                return Page.empty(pageable);
            }
            if (rootCategory.isPresent()) {
                spec = spec.and(ListingSpecifications.inCategories(
                        withAvailableDescendants(rootCategory.get())));
            }
            if (hasText(filter.search())) {
                spec = spec.and(ListingSpecifications.matching(filter.search()));
            }
            if (hasText(filter.sellerId())) {
                spec = spec.and(ListingSpecifications.soldBy(filter.sellerId()));
            }
            if (filter.minPrice() != null) {
                spec = spec.and(ListingSpecifications.pricedAtLeast(filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                spec = spec.and(ListingSpecifications.pricedAtMost(filter.maxPrice()));
            }
            if (filter.attributes() != null && !filter.attributes().isEmpty()) {
                Category category = rootCategory.orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Attribute facets require categoryUuid or categorySlug."));
                for (AttributeFilter attribute : listingFacetValidator.validate(
                        category, filter.attributes())) {
                    spec = spec.and(ListingSpecifications.hasAttribute(attribute.key(), attribute.values()));
                }
            }
        }

        return listingResponseFactory.page(listingRepository.findAll(spec, pageable));
    }

    /**
     * The categories a category filter matches: the one named plus everything beneath
     * it, since listings are filed against leaves while category pages are built from
     * the whole tree.
     *
     * @return empty when no category was named at all; a present-but-empty set when one
     *         was named and does not exist
     */
    private Optional<Category> resolveCategory(ListingFilter filter) {
        Optional<Category> root;
        if (filter.categoryUuid() != null) {
            root = categoryRepository.findByUuidAndIsDeletedFalse(filter.categoryUuid());
        } else if (hasText(filter.categorySlug())) {
            root = categoryRepository.findBySlugAndIsDeletedFalse(filter.categorySlug().trim());
        } else {
            return Optional.empty();
        }
        return root.filter(categoryAvailability::isEffectivelyActive);
    }

    private static boolean hasCategoryFilter(ListingFilter filter) {
        return filter.categoryUuid() != null || hasText(filter.categorySlug());
    }

    /**
     * Walks the category tree breadth-first. The visited set guards the traversal: a
     * parent cycle would otherwise hang the request rather than fail it.
     */
    private Set<UUID> withAvailableDescendants(Category root) {
        Set<UUID> found = new LinkedHashSet<>();
        Deque<Category> pending = new ArrayDeque<>(List.of(root));
        while (!pending.isEmpty()) {
            Category category = pending.poll();
            if (!categoryAvailability.isEffectivelyActive(category)) {
                continue;
            }
            if (!found.add(category.getUuid())) {
                continue;
            }
            if (category.getChildCategories() != null) {
                category.getChildCategories().stream()
                        .filter(categoryAvailability::isEffectivelyActive)
                        .forEach(pending::add);
            }
        }
        return found;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public ListingResponse getListing(UUID uuid) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        return requireVisible(listing);
    }

    @Override
    public ListingResponse getListingBySlug(String slug) {
        Listing listing = listingRepository.findBySlugWithDetails(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        return requireVisible(listing);
    }

    private ListingResponse requireVisible(Listing listing) {
        if (!listingVisibility.isVisible(listing)) {
            // 404 rather than 403: a draft nobody may read should not have its
            // existence confirmed either.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        }
        return listingResponseFactory.one(listing);
    }

    @Override
    @Transactional
    public ListingResponse create(ListingCreateRequest request) {
        Category category = publicCategoryOr404(request.categoryUuid());

        String slug = Utils.toSlug(request.title());
        if (listingRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Listing slug already exists.");
        }

        String sellerId = AuthUtils.extractUserId();
        // Holding the SELLER role is not enough on its own: the role survives a
        // suspension, and posting is what the subscription is sold for. Both are
        // checked before any file is claimed, so a refused request leaves nothing
        // half-attached.
        SellerProfile sellerProfile = sellerAccessGuard.requireActiveSeller(sellerId);
        subscriptionService.requirePostingAllowed(sellerId);

        requireDiscountBelowList(request.fullPrice(), request.discountPrice());

        FileUpload thumbnailFile = fileUploadService.requireOwnedFile(request.thumbnailObjectName(), sellerId);

        Map<String, FileUpload> imageFiles = resolveImageFiles(request.images(), sellerId);

        Listing listing = new Listing();
        listing.setSellerProfile(sellerProfile);
        listing.setCategory(category);
        listing.setTitle(request.title());
        listing.setSlug(slug);
        listing.setDescription(request.description());
        listing.setFullPrice(request.fullPrice());
        listing.setDiscountPrice(request.discountPrice());
        listing.setStockQty(request.stockQty());
        listing.setIsFeatured(request.isFeatured() != null ? request.isFeatured() : false);
        listing.setThumbnailFile(thumbnailFile);
        listing.setStatus(request.stockQty() > 0 ? ListingStatus.ACTIVE : ListingStatus.SOLD_OUT);
        listing.setSold(0);

        // Always run, even with no attributes given: an empty set is exactly what fails
        // the category's required check, and a phone with no storage listed should not
        // reach the catalogue.
        listingAttributeWriter.apply(listing, request.listingAttributes());

        attachImages(listing, request.images(), imageFiles);

        Listing saved = listingRepository.save(listing);
        // Opening balance, so the ledger explains the whole of stock_qty rather than
        // everything after the first sale.
        if (saved.getStockQty() != null && saved.getStockQty() > 0) {
            stockLedger.record(saved, saved.getStockQty(), StockMovementReason.INITIAL,
                    StockChannel.MANUAL, null, null);
        }
        return listingResponseFactory.one(saved);
    }

    @Override
    @Transactional
    public ListingResponse update(UUID uuid, UpdateListingRequest request) {
        Listing listing = listingRepository.findByUuidForEdit(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        String currentSellerId = requireSellerCanEdit(
                listing, "You are not allowed to update this listing.");
        boolean categoryChanged = false;
        if (request.categoryUuid() != null) {
            Category category = publicCategoryOr404(request.categoryUuid());
            categoryChanged = !category.getUuid().equals(listing.getCategory().getUuid());
            listing.setCategory(category);
        }
        if (request.title() != null) {
            String newSlug = Utils.toSlug(request.title());
            if (!newSlug.equals(listing.getSlug()) && listingRepository.existsBySlug(newSlug)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Listing slug already exists.");
            }
            listing.setSlug(newSlug);
            listing.setTitle(request.title());
        }
        if (request.description() != null) {
            listing.setDescription(request.description());
        }
        // Checked against the state the listing ends up in, so dropping the list price
        // under a discount that was already there is caught too.
        if (request.fullPrice() != null || request.discountPrice() != null) {
            BigDecimal fullPrice = request.fullPrice() != null ? request.fullPrice() : listing.getFullPrice();
            BigDecimal discountPrice = request.discountPrice() != null
                    ? request.discountPrice()
                    : listing.getDiscountPrice();
            requireDiscountBelowList(fullPrice, discountPrice);
            listing.setFullPrice(fullPrice);
            listing.setDiscountPrice(discountPrice);
        }
        // A count sent here is the seller's belief about the shelf, so it is applied as
        // the difference from what we hold rather than written over the top. Anything
        // that sold between loading the form and saving it therefore survives.
        if (request.stockQty() != null) {
            int current = listing.getStockQty() == null ? 0 : listing.getStockQty();
            int delta = request.stockQty() - current;
            if (delta != 0) {
                stockLedger.apply(listing, delta,
                        delta > 0 ? StockMovementReason.RESTOCK : StockMovementReason.ADJUST,
                        StockChannel.MANUAL, null, "Set to " + request.stockQty() + " by the seller");
            }
        }
        if (request.status() != null) {
            if (request.status() == ListingStatus.SUSPENDED
                    || request.status() == ListingStatus.REMOVED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only an admin can moderate a listing.");
            }
            // Archived listings do not count against the plan, so bringing one back is
            // the same act as publishing a new one. Without this check a seller could
            // archive their way under the limit and then un-archive everything, ending
            // up with twice the listings their plan allows.
            if (listing.getStatus() == ListingStatus.ARCHIVED
                    && request.status() != ListingStatus.ARCHIVED) {
                subscriptionService.requirePostingAllowed(currentSellerId);
            }
            if (isPublicStatus(request.status())
                    && !categoryAvailability.isEffectivelyActive(listing.getCategory())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This listing's category is not available in the public catalogue.");
            }
            if (request.status() == ListingStatus.ACTIVE && listing.getStockQty() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "An out-of-stock listing cannot be activated. Add stock first.");
            }
            listing.setStatus(request.status());
        } else if (request.stockQty() != null) {
            if (request.stockQty() == 0 && listing.getStatus() == ListingStatus.ACTIVE) {
                listing.setStatus(ListingStatus.SOLD_OUT);
            } else if (request.stockQty() > 0
                    && listing.getStatus() == ListingStatus.SOLD_OUT
                    && categoryAvailability.isEffectivelyActive(listing.getCategory())) {
                listing.setStatus(ListingStatus.ACTIVE);
            }
        }
        if (request.isFeatured() != null) {
            listing.setIsFeatured(request.isFeatured());
        }

        // Re-checked whenever the category moves, because the schema moved with it: what
        // a shirt had to specify and what a phone has to specify are different lists, and
        // the seller supplies the new answers in this same call.
        if (request.listingAttributes() != null) {
            listingAttributeWriter.apply(listing, request.listingAttributes());
        } else if (categoryChanged || (request.status() != null && isPublicStatus(request.status()))) {
            listingAttributeWriter.apply(listing, listingAttributeWriter.currentOf(listing));
        }

        Listing updated = listingRepository.save(listing);
        return listingResponseFactory.one(updated);
    }

    private static boolean isPublicStatus(ListingStatus status) {
        return status == ListingStatus.ACTIVE || status == ListingStatus.SOLD_OUT;
    }

    private Category publicCategoryOr404(UUID categoryUuid) {
        Category category = categoryRepository.findByUuidAndIsDeletedFalse(categoryUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found"));
        if (!categoryAvailability.isEffectivelyActive(category)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category is not active in the public catalogue.");
        }
        return category;
    }

    @Override
    @Transactional
    public ListingResponse clearDiscount(UUID uuid) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        requireSellerCanEdit(listing, "You are not allowed to update this listing.");
        listing.setDiscountPrice(null);
        return listingResponseFactory.one(listingRepository.save(listing));
    }

    @Override
    @Transactional
    public ListingResponse updateThumbnail(UUID uuid, String objectName) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        String currentSellerId = requireSellerCanEdit(
                listing, "You are not allowed to update this listing.");
        FileUpload newFile = fileUploadService.requireOwnedFile(objectName, currentSellerId);
        FileUpload oldFile = listing.getThumbnailFile();
        listing.setThumbnailFile(newFile);
        // Flushed first, so the old file is no longer this listing's thumbnail when
        // it is offered up. It survives if anything else still shows it.
        Listing saved = listingRepository.saveAndFlush(listing);
        if (oldFile != null) {
            fileUploadService.deleteQuietly(oldFile);
        }
        return listingResponseFactory.one(saved);
    }
    @Override
    @Transactional
    public ListingResponse addImage(UUID uuid, AddListingImageRequest request) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        String currentSellerId = requireSellerCanEdit(
                listing, "You are not allowed to update this listing.");
        FileUpload file = fileUploadService.requireOwnedFile(request.objectName(), currentSellerId);
        ListingImage image = new ListingImage();
        image.setFile(file);
        image.setSortOrder(request.sortOrder() != null ? request.sortOrder() : listing.getImages().size());
        image.setListing(listing);
        listing.getImages().add(image);
        Listing saved = listingRepository.save(listing);
        return listingResponseFactory.one(saved);
    }

    @Override
    @Transactional
    public ListingResponse reorderImages(UUID uuid, List<UUID> imageUuids) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        requireSellerCanEdit(listing, "You are not allowed to update this listing.");

        Map<UUID, ListingImage> byUuid = listing.getImages().stream()
                .collect(Collectors.toMap(ListingImage::getUuid, Function.identity()));

        // Insisting on the complete set is what makes this safe to apply wholesale: a
        // partial list would leave the images it omitted holding stale positions, and a
        // repeated id would put two images in the same place.
        Set<UUID> requested = new LinkedHashSet<>(imageUuids);
        if (requested.size() != imageUuids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The same image is listed more than once.");
        }
        if (!requested.equals(byUuid.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The new order must list every image on this listing exactly once. "
                            + "Expected " + byUuid.size() + " image(s), received " + requested.size() + ".");
        }

        int position = 0;
        for (UUID imageUuid : imageUuids) {
            byUuid.get(imageUuid).setSortOrder(position++);
        }

        Listing saved = listingRepository.save(listing);
        return listingResponseFactory.one(saved);
    }

    @Override
    @Transactional
    public void removeImage(UUID listingUuid, UUID imageUuid) {

        Listing listing = listingRepository.findByUuidWithDetails(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        requireSellerCanEdit(listing, "You are not allowed to update this listing.");
        ListingImage toRemove = listing.getImages().stream()
                .filter(img -> img.getUuid().equals(imageUuid))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found on this listing"));

        FileUpload removedFile = toRemove.getFile();
        listing.getImages().remove(toRemove);
        listingRepository.saveAndFlush(listing);
        fileUploadService.deleteQuietly(removedFile);
    }
    /** A discount at or above the list price advertises a saving that is not one. */
    private void requireDiscountBelowList(BigDecimal fullPrice, BigDecimal discountPrice) {
        if (discountPrice != null && fullPrice != null && discountPrice.compareTo(fullPrice) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The discount price (" + discountPrice + ") must be below the full price ("
                            + fullPrice + "). To end the sale, use DELETE /api/v1/listings/"
                            + "{uuid}/discount instead.");
        }
    }

    /**
     * A moderated listing is frozen for its seller: not editable, not re-photographed,
     * not deletable. Deleting especially — that would let a seller erase the listing an
     * admin took down, along with the reason it was taken down.
     */
    private void requireNotModerated(Listing listing) {
        if (listing.getStatus() == ListingStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This listing has been suspended by an administrator and cannot be changed. "
                            + "Reason: " + listing.getModerationReason());
        }
        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This listing has been removed by an administrator and cannot be changed. "
                            + "Reason: " + listing.getModerationReason());
        }
    }

    private String requireSellerCanEdit(Listing listing, String forbiddenMessage) {
        String sellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        requireNotModerated(listing);
        sellerAccessGuard.requireActiveSeller(sellerId);
        return sellerId;
    }

    /**
     * Resolves the client-supplied object names, confirming this seller uploaded
     * each one. Looking them up without that check let a seller attach another
     * user's file to their own listing — and then destroy it, since deleting a
     * listing deletes the objects behind its images.
     */
    private Map<String, FileUpload> resolveImageFiles(List<ListingImageRequest> images, String sellerId) {
        if (images == null || images.isEmpty()) {
            return Map.of();
        }
        List<String> requestedNames = images.stream()
                .map(ListingImageRequest::objectName)
                .toList();
        return fileUploadService.requireOwnedFiles(requestedNames, sellerId).stream()
                .collect(Collectors.toMap(FileUpload::getObjectName, Function.identity()));
    }

    private void attachImages(Listing listing, List<ListingImageRequest> images, Map<String, FileUpload> imageFiles) {
        if (images == null) {
            return;
        }
        for (int index = 0; index < images.size(); index++) {
            ListingImageRequest req = images.get(index);
            ListingImage image = new ListingImage();
            image.setFile(imageFiles.get(req.objectName()));
            // Falls back to the position in the request rather than leaving null, which
            // is what addImage already did. Nulls here were how listings ended up with
            // an unorderable gallery in the first place.
            image.setSortOrder(req.sortOrder() != null ? req.sortOrder() : index);
            image.setListing(listing);
            listing.getImages().add(image);
        }
    }


    @Override
    @Transactional
    public void delete(UUID uuid) {

        // Find the listing
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        // 2. Authorization check
        requireSellerCanEdit(listing, "You are not allowed to delete this listing.");

        // 3. Collect the files this listing was showing
        List<FileUpload> filesToRelease = new ArrayList<>();

        // Add thumbnail
        if (listing.getThumbnailFile() != null) {
            filesToRelease.add(listing.getThumbnailFile());
        }

        // Add all listing images
        if (listing.getImages() != null && !listing.getImages().isEmpty()) {
            listing.getImages().forEach(image -> {
                if (image.getFile() != null) {
                    filesToRelease.add(image.getFile());
                }
            });
        }

        // The listing goes first: offering the files up while its own rows still point
        // at them would find every one still in use and keep the lot.
        listingRepository.delete(listing);
        listingRepository.flush();

        for (FileUpload file : filesToRelease) {
            try {
                fileUploadService.deleteQuietly(file);
            } catch (Exception exception) {
                // A leftover object is cheaper than refusing to delete the listing.
                log.warn("Failed to delete file '{}' after deleting listing {}",
                        file.getObjectName(), uuid, exception);
            }
        }
    }



}

