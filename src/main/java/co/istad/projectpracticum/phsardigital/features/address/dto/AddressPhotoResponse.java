package co.istad.projectpracticum.phsardigital.features.address.dto;

/**
 * @param url     browser-facing link to the image, or null when the upload behind it
 *                has since been deleted
 * @param caption what the courier should notice at this spot
 */
public record AddressPhotoResponse(
        String url,
        String caption
) {
}
