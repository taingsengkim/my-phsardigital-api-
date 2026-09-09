package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportTargetDetailsResponse;
import co.istad.projectpracticum.phsardigital.features.review.Review;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks up what a report actually names, so an admin does not have to.
 *
 * <p>Batched by design. The queue snapshots {@code targetLabel} at filing precisely so
 * rendering it costs no joins, and hydrating one target per row would undo that — a page
 * of twenty reports would become forty-odd queries. Instead the ids on a page are grouped
 * by type and each type is fetched once: at most three queries for the whole page,
 * however long it is.
 *
 * <p>A target that has been deleted resolves to null rather than an error. Reports
 * outlive what they name on purpose, and a complaint about a listing somebody has since
 * removed is exactly the kind an admin still has to close.
 */
@Component
@RequiredArgsConstructor
public class ReportTargetHydrator {

    /**
     * A shop's catalogue size counts everything except what an admin has taken down for
     * good. A suspended listing is still the shop's, and is often exactly what a report
     * is about.
     */
    private static final ListingStatus NOT_COUNTED = ListingStatus.REMOVED;

    private final ListingRepository listingRepository;
    private final SellerRepository sellerRepository;
    private final ReviewRepository reviewRepository;
    private final FileUploadService fileUploadService;

    /**
     * Everything a page of reports points at, keyed by {@code targetId} exactly as the
     * report stores it.
     */
    public Snapshot hydrate(List<ContentReport> reports) {
        List<UUID> listingIds = new ArrayList<>();
        List<String> sellerIds = new ArrayList<>();
        List<UUID> reviewIds = new ArrayList<>();

        for (ContentReport report : reports) {
            switch (report.getTargetType()) {
                case LISTING -> asUuid(report.getTargetId()).ifPresent(listingIds::add);
                case SELLER -> sellerIds.add(report.getTargetId());
                case REVIEW -> asUuid(report.getTargetId()).ifPresent(reviewIds::add);
            }
        }

        Map<String, Listing> listings = new HashMap<>();
        Map<String, SellerProfile> sellers = new HashMap<>();
        Map<String, Review> reviews = new HashMap<>();

        if (!listingIds.isEmpty()) {
            listingRepository.findAllById(listingIds)
                    .forEach(listing -> listings.put(listing.getUuid().toString(), listing));
        }
        if (!sellerIds.isEmpty()) {
            sellerRepository.findAllById(sellerIds)
                    .forEach(seller -> sellers.put(seller.getSellerId(), seller));
        }
        if (!reviewIds.isEmpty()) {
            reviewRepository.findAllById(reviewIds)
                    .forEach(review -> reviews.put(review.getUuid().toString(), review));
        }
        return new Snapshot(listings, sellers, reviews);
    }

    /** @return the full panel, or null when the target has been deleted */
    public ReportTargetDetailsResponse detailsOf(ContentReport report, Snapshot snapshot) {
        return switch (report.getTargetType()) {
            case LISTING -> listingDetails(snapshot.listings.get(report.getTargetId()));
            case SELLER -> sellerDetails(snapshot.sellers.get(report.getTargetId()));
            case REVIEW -> reviewDetails(snapshot.reviews.get(report.getTargetId()));
        };
    }

    private ReportTargetDetailsResponse listingDetails(Listing listing) {
        if (listing == null) {
            return null;
        }
        SellerProfile shop = listing.getSellerProfile();
        return ReportTargetDetailsResponse.listing(
                listing.getTitle(),
                previewUrl(listing.getThumbnailFile()),
                listing.getFullPrice(),
                listing.getDiscountPrice(),
                listing.getStatus() == null ? null : listing.getStatus().name(),
                listing.getCategory() == null ? null : listing.getCategory().getName(),
                shop == null ? null : shop.getSellerId(),
                shop == null ? null : shop.getBusinessName(),
                shopStatus(shop));
    }

    private ReportTargetDetailsResponse sellerDetails(SellerProfile shop) {
        if (shop == null) {
            return null;
        }
        // One extra count, and only on the detail route — the queue never asks for it,
        // which is what keeps this off the per-row path.
        long catalogue = listingRepository
                .countBySellerProfile_SellerIdAndStatusNot(shop.getSellerId(), NOT_COUNTED);
        return ReportTargetDetailsResponse.seller(
                shop.getBusinessName(),
                previewUrl(shop.getLogoFile()),
                shop.getSellerId(),
                shopStatus(shop),
                (int) catalogue);
    }

    private ReportTargetDetailsResponse reviewDetails(Review review) {
        if (review == null) {
            return null;
        }
        SellerProfile shop = review.getSeller();
        return ReportTargetDetailsResponse.review(
                review.getRating(),
                review.getComment(),
                review.getBuyer() == null ? null : review.getBuyer().getFullName(),
                previewUrl(review.getPhoto()),
                shop == null ? null : shop.getSellerId(),
                shop == null ? null : shop.getBusinessName(),
                shopStatus(shop));
    }

    /**
     * A shop's standing in the vocabulary the admin shop list already uses, so the same
     * word means the same thing on both screens.
     *
     * <p>Keyed off the recorded suspension rather than the flag alone: a shop awaiting
     * approval is also inactive and has never been moderated, and calling that
     * "suspended" would put an accusation on the screen that nobody made.
     */
    private static String shopStatus(SellerProfile shop) {
        if (shop == null) {
            return null;
        }
        if (shop.getSuspendedAt() != null) {
            return "SUSPENDED";
        }
        return Boolean.TRUE.equals(shop.getIsActive()) ? "ACTIVE" : "INACTIVE";
    }

    private String previewUrl(FileUpload file) {
        return file == null ? null : fileUploadService.getPreviewUrl(file);
    }

    private static Optional<UUID> asUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException notAUuid) {
            // Filing validates the shape, so this only happens on a row written before
            // that check existed or edited by hand. Skipping it leaves the report
            // renderable from its snapshot rather than failing the whole page.
            return Optional.empty();
        }
    }

    /**
     * One page's worth of targets, already loaded.
     *
     * <p>Static, and holding nothing but the maps: every lookup on it is pure, so a
     * caller's tests can build one without a database or a file service. Anything needing
     * those lives on the hydrator instead — see {@link #detailsOf}.
     */
    public static final class Snapshot {

        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of());

        private final Map<String, Listing> listings;
        private final Map<String, SellerProfile> sellers;
        private final Map<String, Review> reviews;

        private Snapshot(Map<String, Listing> listings, Map<String, SellerProfile> sellers,
                         Map<String, Review> reviews) {
            this.listings = listings;
            this.sellers = sellers;
            this.reviews = reviews;
        }

        /** Every target missing — what a page of reports about deleted things looks like. */
        public static Snapshot empty() {
            return EMPTY;
        }

        /** The shop behind the target, whatever kind of target it is, or null. */
        public SellerProfile shopBehind(ContentReport report) {
            return switch (report.getTargetType()) {
                case LISTING -> {
                    Listing listing = listings.get(report.getTargetId());
                    yield listing == null ? null : listing.getSellerProfile();
                }
                case SELLER -> sellers.get(report.getTargetId());
                case REVIEW -> {
                    Review review = reviews.get(report.getTargetId());
                    yield review == null ? null : review.getSeller();
                }
            };
        }

        /** The reported listing, for the queue's thumbnail and price columns. */
        public Listing listingOf(ContentReport report) {
            return report.getTargetType() == ReportTargetType.LISTING
                    ? listings.get(report.getTargetId())
                    : null;
        }
    }
}
