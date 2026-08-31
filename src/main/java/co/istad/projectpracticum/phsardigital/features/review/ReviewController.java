package co.istad.projectpracticum.phsardigital.features.review;


import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyResponse;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewResponse;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewSummaryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;


    @GetMapping("/listings/{listingUuid}")
    public Page<ReviewResponse> getListingReviews(
            @PathVariable UUID listingUuid,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.getReviewsForListing(listingUuid, pageable);
    }

    /**
     * A product's rating and its star breakdown, over every review rather than the page
     * on screen — the bars above the review list.
     *
     * <p>Literal {@code /summary} segments, so both routes here are matched before the
     * {uuid} and {sellerId} patterns beside them.
     */
    @GetMapping("/listings/{listingUuid}/summary")
    public ReviewSummaryResponse getListingReviewSummary(@PathVariable UUID listingUuid) {
        return reviewService.getListingSummary(listingUuid);
    }

    /** The same, for a whole shop: its badge and the breakdown on its storefront. */
    @GetMapping("/sellers/{sellerId}/summary")
    public ReviewSummaryResponse getSellerReviewSummary(@PathVariable String sellerId) {
        return reviewService.getSellerSummary(sellerId);
    }


    @PostMapping("/listings/{listingUuid}")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse createReview(
            @PathVariable UUID listingUuid,
            @Valid @RequestBody ReviewRequest request) {
        return reviewService.createReview(listingUuid, request);
    }


    @GetMapping("/me")
    public Page<ReviewResponse> getMyReviews(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.getMyReviews(pageable);
    }


    @PatchMapping("/{reviewUuid}")
    public ReviewResponse updateReview(
            @PathVariable UUID reviewUuid,
            @Valid @RequestBody ReviewRequest request) {
        return reviewService.updateReview(reviewUuid, request);
    }


    @DeleteMapping("/{reviewUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReview(@PathVariable UUID reviewUuid) {
        reviewService.deleteReview(reviewUuid);
    }


    @GetMapping("/sellers/me")
    public Page<ReviewResponse> getSellerReviews(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.getSellerReviews(pageable);
    }


    /**
     * A named shop's reviews, for its public shop page — open to anyone, since a shop
     * is browsed before signing in, not after. Declared after {@code /sellers/me} but
     * that ordering is cosmetic: a literal segment outranks a variable one, so
     * {@code /sellers/me} keeps answering for the current seller.
     *
     * <p>The rating these average out to is on the shop itself, at
     * {@code GET /api/v1/sellers/{sellerId}}, rather than here — a page of reviews
     * can only average the page it holds.
     */
    @GetMapping("/sellers/{sellerId}")
    public Page<ReviewResponse> getReviewsForSeller(
            @PathVariable String sellerId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.getReviewsForSeller(sellerId, pageable);
    }


    @PostMapping("/{reviewUuid}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewReplyResponse replyToReview(
            @PathVariable UUID reviewUuid,
            @Valid @RequestBody ReviewReplyRequest request) {
        return reviewService.replyToReview(reviewUuid, request);
    }


    @GetMapping("/{reviewUuid}/replies")
    public List<ReviewReplyResponse> getRepliesForReview(@PathVariable UUID reviewUuid) {
        return reviewService.getRepliesForReview(reviewUuid);
    }
}