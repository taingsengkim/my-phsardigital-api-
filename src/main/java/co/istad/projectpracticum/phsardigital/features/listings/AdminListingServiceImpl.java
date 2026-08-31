package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.dto.AdminListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeWriter;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminListingServiceImpl implements AdminListingService {

    /** The statuses only an admin can set or clear. */
    private static final Set<ListingStatus> MODERATED =
            EnumSet.of(ListingStatus.SUSPENDED, ListingStatus.REMOVED);

    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final CategoryAvailability categoryAvailability;
    private final ListingAttributeWriter listingAttributeWriter;
    private final PurchaseRepository purchaseRepository;
    private final FileUploadService fileUploadService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminListingResponse> list(ListingStatus status, String sellerId, String search,
                                           int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(
                pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return listingRepository.findAll(matching(status, sellerId, search), pageable)
                .map(this::toAdminResponse);
    }

    @Override
    @Transactional
    public ListingResponse suspend(UUID uuid, SuspendRequest request) {
        Listing listing = require(uuid);
        if (MODERATED.contains(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This listing is already " + lower(listing.getStatus())
                            + ". Restore it first if you want to change that.");
        }

        listing.setStatusBeforeSuspension(listing.getStatus());
        listing.setStatus(ListingStatus.SUSPENDED);
        stamp(listing, request.reason());

        log.info("Listing {} suspended by {}", uuid, listing.getModeratedBy());
        return listingMapper.toResponse(listingRepository.save(listing));
    }

    @Override
    @Transactional
    public ListingResponse remove(UUID uuid, SuspendRequest request) {
        Listing listing = require(uuid);
        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This listing is already removed.");
        }

        // Only when nothing is recorded yet: removing an already-suspended listing must
        // keep what it was before the suspension, not overwrite it with SUSPENDED and
        // make a later restore put it back to a moderation state.
        if (listing.getStatusBeforeSuspension() == null) {
            listing.setStatusBeforeSuspension(listing.getStatus());
        }
        listing.setStatus(ListingStatus.REMOVED);
        stamp(listing, request.reason());

        log.info("Listing {} removed by {}", uuid, listing.getModeratedBy());
        return listingMapper.toResponse(listingRepository.save(listing));
    }

    @Override
    @Transactional
    public ListingResponse restore(UUID uuid) {
        // Restore is a read-check-write operation on both moderation state and the
        // listing's complete attribute set. Serialize it with seller edits and schema
        // revalidation instead of deciding from a stale pre-lock snapshot.
        Listing listing = requireForRestore(uuid);
        if (!MODERATED.contains(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This listing is not suspended or removed.");
        }

        // Falls back to ARCHIVED rather than ACTIVE for a row taken down before this
        // column existed: putting it back on the marketplace unasked is the worse of
        // the two guesses.
        ListingStatus restored = listing.getStatusBeforeSuspension() == null
                ? ListingStatus.ARCHIVED
                : listing.getStatusBeforeSuspension();

        // Stock can reach zero while a previously ACTIVE listing is down when an
        // already-accepted order is confirmed. Restore the truthful public state.
        if (restored == ListingStatus.ACTIVE
                && (listing.getStockQty() == null || listing.getStockQty() <= 0)) {
            restored = ListingStatus.SOLD_OUT;
        }

        if (isPublicStatus(restored)) {
            if (!Boolean.TRUE.equals(listing.getSellerProfile().getIsActive())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This listing cannot be restored publicly while its shop is inactive.");
            }
            if (!categoryAvailability.isEffectivelyActive(listing.getCategory())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This listing cannot be restored publicly because its category "
                                + "is not active in the catalogue.");
            }
            try {
                listingAttributeWriter.apply(
                        listing, listingAttributeWriter.currentOf(listing));
            } catch (ResponseStatusException exception) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This listing cannot be restored publicly until its attributes "
                                + "satisfy the current category schema. " + exception.getReason(),
                        exception);
            }
        }

        listing.setStatus(restored);
        listing.setStatusBeforeSuspension(null);
        listing.setModeratedBy(AuthUtils.extractUserId());
        listing.setModeratedAt(LocalDateTime.now());
        listing.setModerationReason(null);

        log.info("Listing {} restored to {} by {}", uuid, restored, listing.getModeratedBy());
        return listingMapper.toResponse(listingRepository.save(listing));
    }

    @Override
    @Transactional
    public void delete(UUID uuid) {
        Listing listing = require(uuid);

        // An order line points at the listing with a non-null key. Erasing it would
        // either fail on the constraint or take the line with it, and a buyer's order
        // that no longer says what was bought is worse than a listing left in REMOVED.
        if (purchaseRepository.existsByItems_Listing_Uuid(uuid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This listing appears in an order and cannot be erased. "
                            + "Remove it instead: PATCH /api/v1/admin/listings/"
                            + uuid + "/remove");
        }

        List<FileUpload> filesToRelease = new ArrayList<>();
        if (listing.getThumbnailFile() != null) {
            filesToRelease.add(listing.getThumbnailFile());
        }
        if (listing.getImages() != null) {
            listing.getImages().stream()
                    .filter(image -> image.getFile() != null)
                    .forEach(image -> filesToRelease.add(image.getFile()));
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
                log.warn("Failed to delete file '{}' after erasing listing {}",
                        file.getObjectName(), uuid, exception);
            }
        }
        log.info("Listing {} erased by {}", uuid, AuthUtils.extractUserId());
    }

    private static Specification<Listing> matching(ListingStatus status, String sellerId,
                                                   String search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (sellerId != null && !sellerId.isBlank()) {
                predicates.add(builder.equal(
                        root.get("sellerProfile").get("sellerId"), sellerId.trim()));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("title")), likePattern(search), '\\'));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Escaped so a title containing {@code %} or {@code _} is searched literally. */
    private static String likePattern(String search) {
        String escaped = search.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private void stamp(Listing listing, String reason) {
        listing.setModeratedBy(AuthUtils.extractUserId());
        listing.setModeratedAt(LocalDateTime.now());
        listing.setModerationReason(reason);
    }

    private static String lower(ListingStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static boolean isPublicStatus(ListingStatus status) {
        return status == ListingStatus.ACTIVE || status == ListingStatus.SOLD_OUT;
    }

    private Listing require(UUID uuid) {
        return listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
    }

    private Listing requireForRestore(UUID uuid) {
        return listingRepository.findByUuidForEdit(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
    }

    private AdminListingResponse toAdminResponse(Listing listing) {
        return new AdminListingResponse(
                listing.getUuid(),
                listing.getTitle(),
                listing.getSlug(),
                listing.getSellerProfile() == null ? null : listing.getSellerProfile().getSellerId(),
                listing.getSellerProfile() == null ? null : listing.getSellerProfile().getBusinessName(),
                listing.getCategory() == null ? null : listing.getCategory().getName(),
                listing.getFullPrice(),
                listing.getDiscountPrice(),
                listing.getStockQty(),
                listing.getSold(),
                listing.getStatus(),
                listing.getStatusBeforeSuspension(),
                fileUploadService.getPreviewUrl(listing.getThumbnailFile()),
                listing.getModeratedBy(),
                listing.getModeratedAt(),
                listing.getModerationReason(),
                listing.getCreatedAt(),
                listing.getLastModifiedAt());
    }
}
