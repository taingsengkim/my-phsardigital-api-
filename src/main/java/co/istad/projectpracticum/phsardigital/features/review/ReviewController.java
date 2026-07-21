package co.istad.projectpracticum.phsardigital.features.review;


import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyResponse;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewResponse;
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
@RequestMapping("/api/v1/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;


    @GetMapping("/listings/{listingUuid}")
    public Page<ReviewResponse> getListingReviews(
            @PathVariable UUID listingUuid,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.getReviewsForListing(listingUuid, pageable);
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