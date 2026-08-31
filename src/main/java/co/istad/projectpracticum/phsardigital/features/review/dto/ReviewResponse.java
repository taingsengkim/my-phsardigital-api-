package co.istad.projectpracticum.phsardigital.features.review.dto;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param isVerifiedPurchase whether this reviewer bought <em>this</em> product in an
 *                           order that completed.
 *
 *                           <p>Narrower than the rule that lets someone review at all:
 *                           the right to review is earned per shop, so that buying one
 *                           size and reviewing the product is possible. The badge claims
 *                           something stricter, because that is what a shopper reads it
 *                           as — that this person actually owns the thing.
 *
 *                           <p>Computed on read like the star ratings are, so it stays
 *                           true when an order is later cancelled, rather than being
 *                           frozen at whatever was true the day the review was written.
 */
public record ReviewResponse(
        UUID uuid,
        ListingResponse listing,
        ReviewAuthorResponse buyer,
        SellerProfileResponse seller,
        Integer rating,
        String comment,
        FileUploadResponse photo,
        Boolean isEdited,
        boolean isVerifiedPurchase,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ReviewReplyResponse> replies // nested replies
) {

    /**
     * The same review with its badge decided.
     *
     * <p>A wither because the mapper builds the response from the entity alone, and
     * whether a purchase backs it is a separate question answered for a whole page at
     * once — the same shape {@code ListingResponse.withRating} takes, and for the same
     * reason: asking per row would be an N+1.
     */
    public ReviewResponse withVerifiedPurchase(boolean verified) {
        return new ReviewResponse(uuid, listing, buyer, seller, rating, comment, photo,
                isEdited, verified, createdAt, updatedAt, replies);
    }
}
