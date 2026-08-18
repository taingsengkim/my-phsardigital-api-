package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * The gallery's new order, as the complete list of image ids from first to last.
 *
 * <p>The whole gallery rather than one image's new position: setting positions one at
 * a time means a sequence of requests that each leave the gallery in a state the seller
 * never asked for, and two images can end up sharing a position if one call fails. The
 * list must name every image on the listing exactly once, so the result is always a
 * gallery the seller described in full.
 */
public record ReorderImagesRequest(
        @NotEmpty(message = "The new image order is required")
        List<UUID> imageUuids
) {
}
