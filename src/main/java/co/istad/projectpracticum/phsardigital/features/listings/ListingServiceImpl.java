package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.config.Utils;
import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.ListingImage;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.AddListingImageRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ListingImageRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final ListingMapper listingMapper;
    private final CategoryRepository categoryRepository;
    private final SellerAccessGuard sellerAccessGuard;
    private final SubscriptionService subscriptionService;
    private final FileUploadService fileUploadService;

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
        return listingPage.map(listingMapper::toResponse);
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
        return listingPage.map(listingMapper::toResponse);
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
    public Page<ListingResponse> getAll(Integer pageNumber, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC,("lastModifiedAt")));
        Page<Listing> listingPage = listingRepository.findByStatus(ListingStatus.ACTIVE,pageable);
        return listingPage.map(listingMapper::toResponse);
    }

    /** The statuses a listing is browsable in. Everything else is private to its shop. */
    private static final Set<ListingStatus> PUBLICLY_VISIBLE =
            EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD_OUT);

    @Override
    public ListingResponse getListing(UUID uuid) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        if (!PUBLICLY_VISIBLE.contains(listing.getStatus()) && !maySeePrivately(listing)) {
            // 404 rather than 403: a draft nobody may read should not have its
            // existence confirmed either.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        }
        return listingMapper.toResponse(listing);
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

    @Override
    public ListingResponse create(ListingCreateRequest request) {
        Category category = categoryRepository.findById(request.categoryUuid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

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

        FileUpload thumbnailFile = fileUploadService.requireOwnedFile(request.thumbnailObjectName(), sellerId);

        Map<String, FileUpload> imageFiles = resolveImageFiles(request.images(), sellerId);

        Listing listing = new Listing();
        listing.setSellerProfile(sellerProfile);
        listing.setCategory(category);
        listing.setTitle(request.title());
        listing.setSlug(slug);
        listing.setDescription(request.description());
        listing.setPrice(request.price());
        listing.setStockQty(request.stockQty());
        listing.setIsFeatured(request.isFeatured() != null ? request.isFeatured() : false);
        listing.setThumbnailFile(thumbnailFile);
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setSold(0);

        if (request.listingAttributes() != null) {
            List<ListingAttribute> attributes = request.listingAttributes().stream()
                    .map(attrDto -> {
                        ListingAttribute attribute = new ListingAttribute();
                        attribute.setKey(attrDto.key());
                        attribute.setValue(attrDto.value());
                        attribute.setSortOrder(attrDto.sortOrder() != null ? attrDto.sortOrder() : 0);
                        attribute.setListing(listing);   // 👈 **CRITICAL** - set the parent
                        return attribute;
                    })
                    .collect(Collectors.toList());
            listing.setListingAttributes(attributes);
        }


        attachImages(listing, request.images(), imageFiles);

        Listing saved = listingRepository.save(listing);
        return listingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ListingResponse update(UUID uuid, UpdateListingRequest request) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        String currentSellerId =  AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(currentSellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update this listing.");
        }
        requireNotSuspended(listing);
        if (request.categoryUuid() != null) {
            Category category = categoryRepository.findById(request.categoryUuid())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
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
        if (request.price() != null) {
            listing.setPrice(request.price());
        }
        if (request.stockQty() != null) {
            listing.setStockQty(request.stockQty());
        }
        if (request.status() != null) {
            if (request.status() == ListingStatus.SUSPENDED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only an admin can suspend a listing.");
            }
            // Archived listings do not count against the plan, so bringing one back is
            // the same act as publishing a new one. Without this check a seller could
            // archive their way under the limit and then un-archive everything, ending
            // up with twice the listings their plan allows.
            if (listing.getStatus() == ListingStatus.ARCHIVED
                    && request.status() != ListingStatus.ARCHIVED) {
                subscriptionService.requirePostingAllowed(currentSellerId);
            }
            listing.setStatus(request.status());
        }
        if (request.isFeatured() != null) {
            listing.setIsFeatured(request.isFeatured());
        }
        Listing updated = listingRepository.save(listing);
        return listingMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public ListingResponse updateThumbnail(UUID uuid, String objectName) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        String currentSellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(currentSellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update this listing.");
        }
        requireNotSuspended(listing);
        FileUpload newFile = fileUploadService.requireOwnedFile(objectName, currentSellerId);
        FileUpload oldFile = listing.getThumbnailFile();
        listing.setThumbnailFile(newFile);
        Listing saved = listingRepository.save(listing);
        if (oldFile != null) {
            fileUploadService.delete(oldFile.getObjectName());
        }
        return listingMapper.toResponse(saved);
    }
    @Override
    @Transactional
    public ListingResponse addImage(UUID uuid, AddListingImageRequest request) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        String currentSellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(currentSellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update this listing.");
        }
        requireNotSuspended(listing);
        FileUpload file = fileUploadService.requireOwnedFile(request.objectName(), currentSellerId);
        ListingImage image = new ListingImage();
        image.setFile(file);
        image.setSortOrder(request.sortOrder() != null ? request.sortOrder() : listing.getImages().size());
        image.setIsPrimary(request.isPrimary() != null ? request.isPrimary() : false);
        image.setListing(listing);
        listing.getImages().add(image);
        Listing saved = listingRepository.save(listing);
        return listingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void removeImage(UUID listingUuid, UUID imageUuid) {

        Listing listing = listingRepository.findByUuidWithDetails(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        String currentSellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(currentSellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update this listing.");
        }
        requireNotSuspended(listing);
        ListingImage toRemove = listing.getImages().stream()
                .filter(img -> img.getUuid().equals(imageUuid))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found on this listing"));

        String objectName = toRemove.getFile().getObjectName();
        listing.getImages().remove(toRemove);
        listingRepository.save(listing);
        fileUploadService.delete(objectName);
    }
    /**
     * A suspended listing is frozen for its seller: not editable, not re-photographed,
     * not deletable. Deleting especially — that would let a seller erase the listing an
     * admin took down, along with the reason it was taken down.
     */
    private void requireNotSuspended(Listing listing) {
        if (listing.getStatus() == ListingStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This listing has been suspended by an administrator and cannot be changed. "
                            + "Reason: " + listing.getModerationReason());
        }
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
        for (ListingImageRequest req : images) {
            ListingImage image = new ListingImage();
            image.setFile(imageFiles.get(req.objectName()));
            image.setSortOrder(req.sortOrder());
            image.setIsPrimary(req.isPrimary() != null ? req.isPrimary() : false);
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
        String currentSellerId = AuthUtils.extractUserId();
        if (!listing.getSellerProfile().getSellerId().equals(currentSellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to delete this listing.");
        }
        requireNotSuspended(listing);

        // 3. Collect file names
        List<String> fileNamesToDelete = new ArrayList<>();

        // Add thumbnail
        if (listing.getThumbnailFile() != null) {
            fileNamesToDelete.add(listing.getThumbnailFile().getObjectName());
        }

        // Add all listing images
        if (listing.getImages() != null && !listing.getImages().isEmpty()) {
            listing.getImages().forEach(image -> {
                if (image.getFile() != null) {
                    fileNamesToDelete.add(image.getFile().getObjectName());
                }
            });
        }


        for (String fileName : fileNamesToDelete) {
            try {
                fileUploadService.delete(fileName);
            } catch (Exception exception) {
                // A leftover object is cheaper than refusing to delete the listing.
                log.warn("Failed to delete file '{}' after deleting listing {}", fileName, uuid, exception);
            }
        }

        listingRepository.delete(listing);
    }



}

