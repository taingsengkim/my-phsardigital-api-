package co.istad.projectpracticum.phsardigital.features.review;

import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyResponse;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ReviewService {
    Page<ReviewResponse> getReviewsForListing(UUID listingUuid, Pageable pageable);

    ReviewResponse createReview(UUID listingUuid, ReviewRequest request);

    Page<ReviewResponse> getMyReviews(Pageable pageable);

    ReviewResponse updateReview(UUID reviewUuid, ReviewRequest request);

    void deleteReview(UUID reviewUuid);

    /**
     * Removes a review as a moderator, without the author check {@link #deleteReview}
     * applies. Reviews are publicly readable, so an abusive one has to be removable by
     * somebody other than the person who wrote it.
     */
    void deleteReviewAsAdmin(UUID reviewUuid);

    Page<ReviewResponse> getSellerReviews(Pageable pageable);

    /**
     * Every review left on a named shop's listings, for its public shop page.
     *
     * @param sellerId the shop's Keycloak subject, the same id
     *                 {@code GET /api/v1/sellers/{sellerId}} takes
     */
    Page<ReviewResponse> getReviewsForSeller(String sellerId, Pageable pageable);

    ReviewReplyResponse replyToReview(UUID reviewUuid, ReviewReplyRequest request);

    List<ReviewReplyResponse> getRepliesForReview(UUID reviewUuid);
}
