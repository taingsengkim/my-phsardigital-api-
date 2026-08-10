package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service interface for managing file uploads and retrieval, backed by
 * MinIO object storage. Handles uploading files, resolving public
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
     * Uploads an image after checking that it is a real, non-empty image within
     * the configured size limit, and stores it under the given folder prefix.
     *
     * @param file   the multipart image to upload
     * @param folder logical prefix inside the bucket, e.g. {@code "avatars"}
     * @return the persisted {@link FileUpload}, ready to attach to an entity
     */
    FileUpload uploadImage(MultipartFile file, String folder);

    /**
     * Resolves the browser-facing URL for the given object name.
     *
     * @param objectName the storage object name (key) of the file
     * @return a URL that can be used to preview/access the file
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

    /**
     * Best-effort cleanup used when a file is being replaced. Unlike
     * {@link #delete(String)} it never throws, so a storage hiccup cannot roll
     * back the business change that made the old file obsolete.
     *
     * @param file the file to remove; ignored when {@code null}
     */
    void deleteQuietly(FileUpload file);

    List<FileUploadResponse> uploadMultiple(List<MultipartFile> files);
}
