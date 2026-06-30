package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for managing file uploads and retrieval, backed by
 * MinIO object storage. Handles uploading files, resolving presigned
 * preview URLs, and deleting stored objects.
 */
public interface FileUploadService {
    /**
     * Uploads a file to object storage and persists its metadata.
     *
     * @param file the multipart file to upload
     * @return the uploaded file's metadata as a {@link FileUploadResponse}
     */
    FileUploadResponse upload(MultipartFile file);

    /**
     * Resolves a presigned preview URL for the given object name, allowing
     * temporary, direct access to the file in storage.
     *
     * @param objectName the storage object name (key) of the file
     * @return a presigned URL that can be used to preview/access the file
     */
    String getPreviewUrl(String objectName);

    /**
     * Retrieves file metadata by its stored object name.
     *
     * @param name the storage object name (key) of the file
     * @return the matching file metadata as a {@link FileUploadResponse}
     */
    FileUploadResponse  getByName(String name);

    /**
     * Deletes a file from object storage and removes its associated metadata.
     *
     * @param name the storage object name (key) of the file to delete
     */
    void delete(String name);
}
