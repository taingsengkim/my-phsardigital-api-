package co.istad.sengkim.phsardigital.features.listings;

import co.istad.sengkim.phsardigital.config.security.AuthUtils;
import co.istad.sengkim.phsardigital.features.categories.Category;
import co.istad.sengkim.phsardigital.features.categories.CategoryRepository;
import co.istad.sengkim.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.sengkim.phsardigital.features.listings.dto.ListingResponse;
import co.istad.sengkim.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.sengkim.phsardigital.features.listings.listing_images.ListingImage;
import co.istad.sengkim.phsardigital.features.listings.listing_images.dto.ListingImageRequest;
import co.istad.sengkim.phsardigital.features.seller.SellerProfile;
import co.istad.sengkim.phsardigital.features.seller.SellerRepository;
import com.google.common.base.FinalizablePhantomReference;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService{
    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;

    @Override
    public Page<ListingResponse> getAllListingsByStatus(String status, Integer pageNumber, Integer pageSize) {
        ListingStatus listingStatus;
        try {
            listingStatus = ListingStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid listing status: " + status);
        }
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Listing> listingPage = listingRepository.findByStatus(listingStatus, pageable);

        return listingPage.map(listingMapper::toResponse);
    }

    @Override
    public Page<ListingResponse> getAll(Integer pageNumber, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Listing> listingPage = listingRepository.findByStatus(ListingStatus.ACTIVE,pageable);
        return listingPage.map(listingMapper::toResponse);
    }

    @Override
    public ListingResponse getListing(UUID uuid) {
        Listing listing = listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        return listingMapper.toResponse(listing);
    }

    @Override
    public ListingResponse create(ListingCreateRequest request) {
        Category category = categoryRepository.findById(request.categoryUuid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        Listing listing = new Listing();
        SellerProfile sellerProfile = sellerRepository.findById(AuthUtils.extractUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Seller profile not found. Please complete seller registration first."));
        listing.setSellerProfile(sellerProfile);
        listing.setSellerProfile(new SellerProfile(AuthUtils.extractUserId()));
        listing.setCategory(category);
        listing.setTitle(request.title());
        listing.setSlug(request.slug());
        listing.setDescription(request.description());
        listing.setPrice(request.price());
        listing.setStockQty(request.stockQty());
        listing.setIsFeatured(request.isFeatured() != null ? request.isFeatured() : false);
        listing.setThumbnailObjectName(request.thumbnailUrl());
        listing.setStatus(ListingStatus.DRAFT);
        listing.setSoldCount(0);

        attachImages(listing, request.images());

        Listing saved = listingRepository.save(listing);

        return listingMapper.toResponse(saved);
    }

    private void attachImages(Listing listing, List<ListingImageRequest> images) {
        if (images == null) {
            return;
        }

        for (ListingImageRequest req : images) {
            ListingImage image = new ListingImage();
            image.setObjectName(req.objectName());
            image.setSortOrder(req.sortOrder());
            image.setIsPrimary(req.isPrimary() != null ? req.isPrimary() : false);
            image.setListing(listing);
            listing.getImagesList().add(image);
        }
    }

}
