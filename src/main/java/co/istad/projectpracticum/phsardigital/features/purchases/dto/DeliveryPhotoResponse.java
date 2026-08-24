package co.istad.projectpracticum.phsardigital.features.purchases.dto;

/**
 * A landmark shot of the delivery point, shown to whoever is taking the order out.
 *
 * @param url     browser-facing link to the image, or null when the upload behind it
 *                has since been deleted
 * @param caption what to look for, e.g. "blue gate past the pagoda"
 */
public record DeliveryPhotoResponse(
        String url,
        String caption
) {
}
