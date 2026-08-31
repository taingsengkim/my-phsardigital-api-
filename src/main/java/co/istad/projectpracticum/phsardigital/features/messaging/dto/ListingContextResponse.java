package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The product card shown above a message, identifying what the sender was looking at
 * when they wrote it.
 *
 * <p>Deliberately not {@code ListingResponse}. A chat bubble needs a thumbnail, a name
 * and a price, and nothing else — sending the full listing would drag its attributes,
 * gallery and category through every message in a thread. It is also a snapshot of
 * nothing: the fields are read live, so a price that changes is reflected the next time
 * the thread is opened. That is the right behaviour here, because the card is a link to
 * a product rather than a record of a transaction.
 *
 * @param price     what the listing costs now, discount applied
 * @param fullPrice the undiscounted price, so a client can strike it through. Equal to
 *                  {@code price} when nothing is discounted.
 * @param status    lets the client mark a card whose product is no longer buyable,
 *                  rather than sending the buyer to a dead page
 */
public record ListingContextResponse(
        UUID uuid,
        String title,
        String slug,
        String thumbnailUrl,
        BigDecimal price,
        BigDecimal fullPrice,
        ListingStatus status
) {

    /**
     * @param thumbnailUrl resolved by the caller, which is the only place that holds a
     *                     {@code FileUploadService}
     */
    public static ListingContextResponse of(Listing listing, String thumbnailUrl) {
        return new ListingContextResponse(
                listing.getUuid(),
                listing.getTitle(),
                listing.getSlug(),
                thumbnailUrl,
                listing.effectivePrice(),
                listing.getFullPrice(),
                listing.getStatus());
    }
}
