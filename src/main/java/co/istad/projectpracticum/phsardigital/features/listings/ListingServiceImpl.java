package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.config.Utils;
import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadRepository;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingCreateRequest;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.ListingImage;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.AddListingImageRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ListingImageRequest;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
public class ListingServiceImpl implements ListingService{
    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;
    private final FileUploadService fileUploadService;
    private final FileUploadRepository fileUploadRepository;

    @Override
    public Page<ListingResponse> getAllListingsByStatus(String status, Integer pageNumber, Integer pageSize) {
        ListingStatus listingStatus;
        try {
            listingStatus = ListingStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid listing status: " + status);
        }
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC,("lastModifiedAt")));
        Page<Listing> listingPage = listingRepository.findByStatus(listingStatus, pageable);
        return listingPage.map(listingMapper::toResponse);
    }

    @Override
    public Page<ListingResponse> getAll(Integer pageNumber, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC,("lastModifiedAt")));
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

        String slug = Utils.toSlug(request.title());
        if (listingRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Listing slug already exists.");
        }

        SellerProfile sellerProfile = sellerRepository.findById(AuthUtils.extractUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Seller profile not found. Please complete seller registration first."));

        FileUpload thumbnailFile = fileUploadRepository.findByObjectName(request.thumbnailUrl())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Thumbnail file not found: " + request.thumbnailUrl() + ". Please upload it first."));

        Map<String, FileUpload> imageFiles = resolveImageFiles(request.images());

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
        listing.setStatus(ListingStatus.DRAFT);
        listing.setSold(0);

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
        FileUpload newFile = fileUploadRepository.findByObjectName(objectName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thumbnail file not found: " + objectName + ". Please upload it first."));
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
        FileUpload file = fileUploadRepository.findByObjectName(request.objectName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image file not found: " + request.objectName() + ". Please upload it first."));
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
        ListingImage toRemove = listing.getImages().stream()
                .filter(img -> img.getUuid().equals(imageUuid))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found on this listing"));

        String objectName = toRemove.getFile().getObjectName();
        listing.getImages().remove(toRemove);
        listingRepository.save(listing);
        fileUploadService.delete(objectName);
    }
    private Map<String, FileUpload> resolveImageFiles(List<ListingImageRequest> images) {
        if (images == null || images.isEmpty()) {
            return Map.of();
        }
        List<String> requestedNames = images.stream()
                .map(ListingImageRequest::objectName)
                .toList();
        List<FileUpload> found = fileUploadRepository.findAllByObjectNameIn(requestedNames);
        Map<String, FileUpload> foundByName = found.stream()
                .collect(Collectors.toMap(FileUpload::getObjectName, Function.identity()));
        List<String> missing = requestedNames.stream()
                .filter(name -> !foundByName.containsKey(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The following image files were not found, please upload them first: " + missing);
        }
        return foundByName;
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

}
