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

    Page<ReviewResponse> getSellerReviews(Pageable pageable);

    ReviewReplyResponse replyToReview(UUID reviewUuid, ReviewReplyRequest request);

    List<ReviewReplyResponse> getRepliesForReview(UUID reviewUuid);
}
