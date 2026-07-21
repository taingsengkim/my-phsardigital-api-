package co.istad.projectpracticum.phsardigital.features.review;



import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadRepository;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyResponse;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewResponse;
import co.istad.projectpracticum.phsardigital.features.review.review_reply.ReviewReply;
import co.istad.projectpracticum.phsardigital.features.review.review_reply.ReviewReplyMapper;
import co.istad.projectpracticum.phsardigital.features.review.review_reply.ReviewReplyRepository;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileRepository;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final ListingRepository listingRepository;
    private final UserProfileRepository userRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final FileUploadRepository fileUploadRepository;
    private final ReviewMapper reviewMapper;
    private final ReviewReplyMapper replyMapper;

    @Override
    public Page<ReviewResponse> getReviewsForListing(UUID listingUuid, Pageable pageable) {
        Listing listing = listingRepository.findById(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        return reviewRepository.findByListing(listing, pageable)
                .map(reviewMapper::toResponse);
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

        // 5. Build review entity
        Review review = new Review();
        review.setListing(listing);
        review.setBuyer(buyer);
        review.setSeller(listing.getSellerProfile()); // denormalize
        review.setRating(request.rating());
        review.setComment(request.comment());

        // 6. Handle photo if provided
        if (request.photoObjectName() != null) {
            FileUpload photo = fileUploadRepository.findByObjectName(request.photoObjectName())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Photo not found: " + request.photoObjectName()));
            review.setPhoto(photo);
        }

        Review saved = reviewRepository.save(review);

        // 7. Update listing rating aggregate
//        updateListingRating(listing);

        return reviewMapper.toResponse(saved);
    }

    @Override
    public Page<ReviewResponse> getMyReviews(Pageable pageable) {
        String userId = AuthUtils.extractUserId();
        UserProfile buyer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return reviewRepository.findByBuyer(buyer, pageable)
                .map(reviewMapper::toResponse);
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
            FileUpload photo = fileUploadRepository.findByObjectName(request.photoObjectName())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Photo not found: " + request.photoObjectName()));
            review.setPhoto(photo);
        }
        review.setIsEdited(true);

        Review updated = reviewRepository.save(review);
//        updateListingRating(review.getListing());
        return reviewMapper.toResponse(updated);
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

    @Override
    public Page<ReviewResponse> getSellerReviews(Pageable pageable) {
        String userId = AuthUtils.extractUserId();
        SellerProfile seller = sellerProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Seller profile not found"));
        return reviewRepository.findBySeller(seller, pageable)
                .map(reviewMapper::toResponse);
    }

    @Override
    @Transactional
    public ReviewReplyResponse replyToReview(UUID reviewUuid, ReviewReplyRequest request) {
        // 1. Get current user as seller
        String userId = AuthUtils.extractUserId();
        SellerProfile seller = sellerProfileRepository.findById(userId)
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