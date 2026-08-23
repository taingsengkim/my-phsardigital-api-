package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingResponseFactory;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Consumer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerProfileServiceImpl implements SellerProfileService{

    private final SellerProfileRepository sellerProfileRepository;
    private final ListingRepository listingRepository;
    private final ListingResponseFactory listingResponseFactory;
    private final SellerProfileMapper sellerProfileMapper;
    private final FileUploadService fileUploadService;
    private final ReviewRepository reviewRepository;
    private final ShopLocationResolver shopLocationResolver;
    private final CategoryRepository categoryRepository;
    private final CategoryAvailability categoryAvailability;

    @Override
    public SellerProfileResponse getPublicProfile(String sellerId) {
        SellerProfile profile = sellerProfileRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shop not found"));
        return withRating(profile);
    }

    @Override
    public Page<ListingResponse> getSellerListings(String sellerId, Pageable pageable) {
        // Verify seller exists (if not, return empty or 404 – we choose 404)
        SellerProfile seller = sellerProfileRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shop not found"));

        // A suspended shop keeps its page — the profile already reports isActive, so a
        // client can say why it is empty — but stops offering anything for sale.
        if (!Boolean.TRUE.equals(seller.getIsActive())) {
            return Page.empty(pageable);
        }

        Set<UUID> publicCategoryUuids = categoryRepository
                .findAllByIsDeletedFalseAndIsActiveTrue(Sort.unsorted())
                .stream()
                .filter(categoryAvailability::isEffectivelyActive)
                .map(Category::getUuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (publicCategoryUuids.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<Listing> listings = listingRepository
                .findPublicBySeller(
                        seller, ListingStatus.ACTIVE, 0, publicCategoryUuids, pageable);
        return listingResponseFactory.page(listings);
    }

    @Override
    public SellerProfileResponse getMyProfile() {
        String userId = AuthUtils.extractUserId(); // Keycloak sub
        SellerProfile profile = sellerProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Seller profile not found for this user"));
        return withRatingForOwner(profile);
    }

    @Override
    @Transactional
    public SellerProfileResponse updateMyProfile(SellerProfileUpdateRequest request) {
        String userId = AuthUtils.extractUserId();
        SellerProfile profile = sellerProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Seller profile not found"));

        // Partially update only fields that are not null in request
        sellerProfileMapper.updateFromRequest(request, profile);

        // After the mapper, which copies latitude and longitude across verbatim: when
        // only a link was sent, this is what turns it into a position.
        shopLocationResolver.resolve(request.latitude(), request.longitude(), request.googleMapUrl())
                .ifPresent(coordinates -> {
                    profile.setLatitude(coordinates.latitude());
                    profile.setLongitude(coordinates.longitude());
                });

        // Resolved before the write so an image belonging to somebody else fails the
        // whole request instead of half-applying the rest of the patch.
        FileUpload replacedLogo = applyImage(
                request.logoObjectName(), profile.getLogoFile(), profile::setLogoFile, userId);
        FileUpload replacedCover = applyImage(
                request.coverObjectName(), profile.getCoverFile(), profile::setCoverFile, userId);

        SellerProfile updated = sellerProfileRepository.saveAndFlush(profile);

        // Flushed first, so the old rows are no longer referenced when they are removed.
        if (replacedLogo != null) {
            fileUploadService.deleteQuietly(replacedLogo);
        }
        if (replacedCover != null) {
            fileUploadService.deleteQuietly(replacedCover);
        }
        return withRatingForOwner(updated);
    }

    /**
     * Attaches the shop's star rating. Two aggregates rather than one row with both,
     * because {@code AVG} over no reviews is null while {@code COUNT} is zero, and
     * keeping them separate says that plainly instead of hiding it in a projection.
     */
    private SellerProfileResponse withRating(SellerProfile profile) {
        return sellerProfileMapper.toResponseWithRating(
                profile,
                reviewRepository.averageRatingForSeller(profile.getSellerId()),
                reviewRepository.countBySeller_SellerId(profile.getSellerId()));
    }

    /** As {@link #withRating}, for the routes answering a seller about their own shop. */
    private SellerProfileResponse withRatingForOwner(SellerProfile profile) {
        return sellerProfileMapper.toOwnerResponse(
                profile,
                reviewRepository.averageRatingForSeller(profile.getSellerId()),
                reviewRepository.countBySeller_SellerId(profile.getSellerId()));
    }

    /**
     * Points one of the shop's images — the logo or the cover — at a new file, if one
     * was supplied.
     *
     * @return the file that was displaced and should now be deleted, or null when
     *         nothing changed
     */
    private FileUpload applyImage(String objectName, FileUpload previous,
                                  Consumer<FileUpload> attach, String ownerId) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        if (previous != null && objectName.equals(previous.getObjectName())) {
            return null;
        }
        attach.accept(fileUploadService.requireOwnedFile(objectName, ownerId));
        return previous;
    }
}
