package co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto;

import java.util.UUID;

public record ThumbnailImageResponse (
        String uuid,
        String objectName,
        String uri
        ){
}
