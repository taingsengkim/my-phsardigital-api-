package co.istad.projectpracticum.phsardigital.features.review;



import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.listings.ListingVisibility;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyResponse;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewResponse;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.review.review_reply.ReviewReply;
import co.istad.projectpracticum.phsardigital.features.review.review_reply.ReviewReplyMapper;
import co.istad.projectpracticum.phsardigital.features.review.review_reply.ReviewReplyRepository;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    /** The order state that earns a buyer the right to review the shop. */
    private static final PurchaseStatus QUALIFYING_PURCHASE = PurchaseStatus.COMPLETED;

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final ListingRepository listingRepository;
    private final UserProfileRepository userRepository;
    private final SellerRepository sellerRepository;
    private final FileUploadService fileUploadService;
    private final ReviewMapper reviewMapper;
    private final ReviewReplyMapper replyMapper;
    private final PurchaseRepository purchaseRepository;
    private final ListingVisibility listingVisibility;

    @Override
    public Page<ReviewResponse> getReviewsForListing(UUID listingUuid, Pageable pageable) {
        Listing listing = listingRepository.findById(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (!listingVisibility.isVisible(listing)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        }
        return withVerifiedBadges(reviewRepository.findByListing(listing, pageable));
    }

    @Override
    public ReviewSummaryResponse getListingSummary(UUID listingUuid) {
        Listing listing = listingRepository.findById(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (!listingVisibility.isVisible(listing)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        }
        return ReviewSummaryResponse.of(
                toStarCounts(reviewRepository.ratingBreakdownForListing(listingUuid)));
    }

    @Override
    public ReviewSummaryResponse getSellerSummary(String sellerId) {
        // Resolved first for the same reason getReviewsForSeller does it: an unknown shop
        // should answer 404, not an empty breakdown that reads as "nobody has reviewed it".
        sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        return ReviewSummaryResponse.of(
                toStarCounts(reviewRepository.ratingBreakdownForSeller(sellerId)));
    }

    private static Map<Integer, Long> toStarCounts(List<Object[]> rows) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Override
    @Transactional
    public ReviewResponse createReview(UUID listingUuid, ReviewRequest request) {
        // 1. Get current user
        String userId = AuthUtils.extractUserId();
        UserProfile buyer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 2. Find listing
        Listing listing = listingRepository.findById(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        // 3. Verify listing is ACTIVE
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot review inactive listing");
        }

        // 4. Check if already reviewed
        if (reviewRepository.existsByListingAndBuyer(listing, buyer)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have already reviewed this listing");
        }

        // 5. Must have actually dealt with this shop
        requireHasBoughtFrom(userId, listing.getSellerProfile().getSellerId());

        // 6. Build review entity
        Review review = new Review();
        review.setListing(listing);
        review.setBuyer(buyer);
        review.setSeller(listing.getSellerProfile()); // denormalize
        review.setRating(request.rating());
        review.setComment(request.comment());

        // 7. Handle photo if provided
        if (request.photoObjectName() != null) {
            review.setPhoto(fileUploadService.requireOwnedFile(request.photoObjectName(), userId));
        }

        Review saved = reviewRepository.save(review);

        // 7. Update listing rating aggregate
//        updateListingRating(listing);

        return reviewMapper.toResponse(saved)
                .withVerifiedPurchase(isVerifiedPurchase(userId, listingUuid));
    }

    @Override
    public Page<ReviewResponse> getMyReviews(Pageable pageable) {
        String userId = AuthUtils.extractUserId();
        UserProfile buyer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return withVerifiedBadges(reviewRepository.findByBuyer(buyer, pageable));
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(UUID reviewUuid, ReviewRequest request) {
        String userId = AuthUtils.extractUserId();
        UserProfile buyer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Review review = reviewRepository.findByUuidAndBuyer(reviewUuid, buyer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Review not found or not owned by you"));

        if (request.rating() != null) {
            review.setRating(request.rating());
        }
        if (request.comment() != null) {
            review.setComment(request.comment());
        }
        if (request.photoObjectName() != null) {
            review.setPhoto(fileUploadService.requireOwnedFile(request.photoObjectName(), userId));
        }
        review.setIsEdited(true);

        Review updated = reviewRepository.save(review);
//        updateListingRating(review.getListing());
        return reviewMapper.toResponse(updated)
                .withVerifiedPurchase(
                        isVerifiedPurchase(userId, review.getListing().getUuid()));
    }

    @Override
    @Transactional
    public void deleteReview(UUID reviewUuid) {
        String userId = AuthUtils.extractUserId();
        UserProfile buyer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Review review = reviewRepository.findByUuidAndBuyer(reviewUuid, buyer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Review not found or not owned by you"));

        Listing listing = review.getListing();
        reviewRepository.delete(review);
//        updateListingRating(listing);
    }

    /**
     * Attaches the verified-purchase badge to a whole page in one query.
     *
     * <p>Asking per review would be an N+1 on a list that is paged precisely because it
     * gets long. The pairs that matter are (reviewer, product); the database is handed
     * both sets and returns only the pairs that genuinely have a completed order behind
     * them, so the cross product never materialises here.
     */
    private Page<ReviewResponse> withVerifiedBadges(Page<Review> reviews) {
        List<Review> content = reviews.getContent();
        if (content.isEmpty()) {
            return reviews.map(reviewMapper::toResponse);
        }

        Set<String> buyerIds = content.stream()
                .map(review -> review.getBuyer().getId())
                .collect(Collectors.toSet());
        Set<UUID> listingUuids = content.stream()
                .map(review -> review.getListing().getUuid())
                .collect(Collectors.toSet());

        Set<String> verified = purchaseRepository
                .completedPurchasePairs(QUALIFYING_PURCHASE, buyerIds, listingUuids).stream()
                .map(row -> verifiedKey((String) row[0], (UUID) row[1]))
                .collect(Collectors.toSet());

        return reviews.map(review -> reviewMapper.toResponse(review)
                .withVerifiedPurchase(verified.contains(
                        verifiedKey(review.getBuyer().getId(), review.getListing().getUuid()))));
    }

    /** Whether one reviewer bought one product in an order that completed. */
    private boolean isVerifiedPurchase(String buyerId, UUID listingUuid) {
        return !purchaseRepository.completedPurchasePairs(
                QUALIFYING_PURCHASE, Set.of(buyerId), Set.of(listingUuid)).isEmpty();
    }

    private static String verifiedKey(String buyerId, UUID listingUuid) {
        return buyerId + '|' + listingUuid;
    }

    /**
     * Insists the reviewer has actually dealt with this shop.
     *
     * <p>Judged per shop rather than per listing: having bought from a seller earns
     * you a say on what they sell, and tying it to the exact listing would block the
     * common case of buying one size and reviewing the product.
     *
     * <p>{@code COMPLETED} rather than any order at all, because it is the only
     * terminal state â€” {@code cancel} still accepts a {@code PENDING} or
     * {@code CONFIRMED} order, so anything looser would let someone order, review, and
     * cancel for a free opinion.
     */
    private void requireHasBoughtFrom(String buyerId, String sellerId) {
        boolean bought = purchaseRepository.existsByBuyerIdAndSellerProfile_SellerIdAndStatus(
                buyerId, sellerId, QUALIFYING_PURCHASE);
        if (!bought) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only review a shop you have bought from. "
                            + "This applies once an order from this shop is completed.");
        }
    }

    @Override
    @Transactional
    public void deleteReviewAsAdmin(UUID reviewUuid) {
        Review review = reviewRepository.findById(reviewUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

        log.info("Review {} on listing {} removed by admin {}",
                reviewUuid, review.getListing().getUuid(), AuthUtils.extractUserId());
        reviewRepository.delete(review);
    }

    @Override
    public Page<ReviewResponse> getSellerReviews(Pageable pageable) {
        String userId = AuthUtils.extractUserId();
        SellerProfile seller = sellerRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Seller profile not found"));
        return withVerifiedBadges(reviewRepository.findBySeller(seller, pageable));
    }

    @Override
    public Page<ReviewResponse> getReviewsForSeller(String sellerId, Pageable pageable) {
        // Resolved rather than queried by id straight off, so an unknown shop answers
        // 404 instead of an empty page that looks like a shop nobody has reviewed.
        SellerProfile seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Shop not found"));
        return withVerifiedBadges(reviewRepository.findBySeller(seller, pageable));
    }

    @Override
    @Transactional
    public ReviewReplyResponse replyToReview(UUID reviewUuid, ReviewReplyRequest request) {
        // 1. Get current user as seller
        String userId = AuthUtils.extractUserId();
        SellerProfile seller = sellerRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Seller profile not found"));

        // 2. Find review
        Review review = reviewRepository.findById(reviewUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

        // 3. Verify seller owns the listing
        if (!review.getListing().getSellerProfile().getSellerId().equals(seller.getSellerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not the seller of this listing");
        }

        // 4. Build reply
        ReviewReply reply = new ReviewReply();
        reply.setReview(review);
        reply.setSeller(seller);
        reply.setComment(request.comment());

        // 5. Handle nested reply
        if (request.parentReplyUuid() != null) {
            ReviewReply parent = reviewReplyRepository.findById(request.parentReplyUuid())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Parent reply not found"));
            if (!parent.getReview().getUuid().equals(reviewUuid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parent reply does not belong to this review");
            }
            reply.setParentReply(parent);
        }

        ReviewReply saved = reviewReplyRepository.save(reply);
        return replyMapper.toResponse(saved);
    }

    @Override
    public List<ReviewReplyResponse> getRepliesForReview(UUID reviewUuid) {
        // 1. Fetch all replies for the review
        List<ReviewReply> allReplies = reviewReplyRepository.findByReview_UuidOrderByCreatedAtAsc(reviewUuid);

        // 2. Build a map for quick lookup
        Map<UUID, ReviewReply> map = allReplies.stream()
                .collect(Collectors.toMap(ReviewReply::getUuid, r -> r));

        // 3. Filter roots (those with parentReply == null)
        List<ReviewReply> roots = allReplies.stream()
                .filter(r -> r.getParentReply() == null)
                .collect(Collectors.toList());

        // 4. Map to response tree using recursion
        return roots.stream()
                .map(this::buildReplyTree)
                .collect(Collectors.toList());
    }

    private ReviewReplyResponse buildReplyTree(ReviewReply reply) {
        ReviewReplyResponse response = replyMapper.toResponse(reply);
        if (reply.getChildReplies() != null && !reply.getChildReplies().isEmpty()) {
            List<ReviewReplyResponse> childResponses = reply.getChildReplies().stream()
                    .map(this::buildReplyTree)
                    .collect(Collectors.toList());

            return new ReviewReplyResponse(
                    response.uuid(),
                    response.comment(),
                    response.seller(),
                    response.createdAt(),
                    response.updatedAt(),
                    response.parentReplyUuid(),
                    childResponses
            );
        }
        return response;
    }

    // Helper method to update rating aggregation
//    private void updateListingRating(Listing listing) {
//        Double avg = reviewRepository.calculateAverageRating(listing);
//        long count = reviewRepository.countByListing(listing);
//        listing.setAverageRating(avg != null ? avg : 0.0);
//        listing.setReviewCount((int) count);
//        listingRepository.save(listing);
//    }
}
