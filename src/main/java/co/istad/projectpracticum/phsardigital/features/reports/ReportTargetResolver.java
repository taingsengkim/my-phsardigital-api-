package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.review.Review;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Turns a {@code targetType} and {@code targetId} into the thing they name.
 *
 * <p>Exists so a report cannot be filed against something that does not exist. Without
 * this the queue fills with complaints about listings that were never there, and an
 * admin has to establish that one at a time; and since the label is snapshotted at
 * filing, the target has to be loaded at that moment anyway.
 *
 * <p>It also reports who owns the target, which is what lets the service refuse
 * somebody reporting their own shop.
 */
@Component
@RequiredArgsConstructor
public class ReportTargetResolver {

    /** The label column holds 200; a long product title is trimmed, not refused. */
    private static final int MAX_LABEL = 200;

    private final ListingRepository listingRepository;
    private final SellerRepository sellerRepository;
    private final ReviewRepository reviewRepository;

    /**
     * @param label   what the target is called, for the admin queue
     * @param ownerId whoever the report is effectively about — the shop behind a
     *                listing, the shop itself, the author of a review
     */
    public record ResolvedTarget(String label, String ownerId) {
    }

    /**
     * @throws ResponseStatusException 404 when nothing of that type has that id, or
     *         400 when the id is not even the right shape for the type
     */
    public ResolvedTarget resolve(ReportTargetType targetType, String targetId) {
        return switch (targetType) {
            case LISTING -> {
                Listing listing = listingRepository.findById(asUuid(targetId, "listing"))
                        .orElseThrow(() -> notFound("listing"));
                yield new ResolvedTarget(trim(listing.getTitle()),
                        listing.getSellerProfile().getSellerId());
            }
            case SELLER -> {
                SellerProfile seller = sellerRepository.findById(targetId)
                        .orElseThrow(() -> notFound("shop"));
                yield new ResolvedTarget(trim(seller.getBusinessName()), seller.getSellerId());
            }
            case REVIEW -> {
                Review review = reviewRepository.findById(asUuid(targetId, "review"))
                        .orElseThrow(() -> notFound("review"));
                // getId() on a lazy proxy reads the foreign key already in hand rather
                // than loading the buyer's row.
                yield new ResolvedTarget(labelOf(review), review.getBuyer().getId());
            }
        };
    }

    /** A review's own words, or its rating when it was left as stars alone. */
    private static String labelOf(Review review) {
        String comment = review.getComment();
        return comment == null || comment.isBlank()
                ? review.getRating() + "-star review"
                : trim(comment);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_LABEL ? trimmed : trimmed.substring(0, MAX_LABEL).trim();
    }

    /**
     * A listing and a review are addressed by UUID. Saying so beats the 500 that
     * {@link UUID#fromString} would otherwise raise from inside the service.
     */
    private static UUID asUuid(String targetId, String what) {
        try {
            return UUID.fromString(targetId);
        } catch (IllegalArgumentException malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A " + what + " is identified by a UUID, but '" + targetId + "' is not one.");
        }
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "There is no such " + what + " to report.");
    }
}
