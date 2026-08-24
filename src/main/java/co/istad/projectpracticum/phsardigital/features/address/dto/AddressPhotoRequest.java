package co.istad.projectpracticum.phsardigital.features.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One landmark photo on a saved address. The image is uploaded first through the file
 * endpoint; this carries only the object name it came back with, which is checked
 * against the caller before it is attached.
 *
 * @param objectName the storage key returned by the image upload
 * @param caption    what the courier should notice, e.g. "blue gate past the pagoda"
 */
public record AddressPhotoRequest(
        @NotBlank(message = "Photo object name is required")
        @Size(max = 500, message = "Photo object name must not exceed 500 characters")
        String objectName,

        @Size(max = 120, message = "Caption must not exceed 120 characters")
        String caption
) {
}
