package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

/**
 * Service interface for managing file uploads and retrieval, backed by
 * MinIO object storage. Handles uploading files, resolving public
 * preview URLs, and deleting stored objects.
 *
 * <p>Every upload path validates the file's actual bytes against an allowlist
 * before it is stored, and the type written to storage is the detected one — not
 * the one the client claimed. Uploads split into two families: images, which are
 * anonymously readable, and documents, which are not.
 */
public interface FileUploadService {
    /**
     * Uploads an image to the public bucket and persists its metadata. Applies the
     * same validation as {@link #uploadImage(MultipartFile, String)} at the bucket
     * root.
     *
     * @param file the multipart image to upload
     * @return the uploaded file's metadata as a {@link FileUploadResponse}
     */
    FileUploadResponse upload(MultipartFile file);

    /**
     * Uploads an image after confirming its bytes really are one of the allowed
     * image formats and that it is within the configured size limit, and stores it
     * under the given folder prefix in the public bucket.
     *
     * @param file   the multipart image to upload
     * @param folder logical prefix inside the bucket, e.g. {@code "avatars"}
     * @return the persisted {@link FileUpload}, ready to attach to an entity
     * @throws org.springframework.web.server.ResponseStatusException 413 when it is
     *         too large, 415 when the bytes are not an allowed image
     */
    FileUpload uploadImage(MultipartFile file, String folder);

    /**
     * Uploads a supporting document — an identity card, a business licence — into
     * the private bucket. Documents are never anonymously readable: the only way
     * to view one afterwards is a short-lived presigned URL from
     * {@link #getPrivateUrl(String)}.
     *
     * @param file   the multipart document to upload
     * @param folder logical prefix inside the bucket, e.g. {@code "documents"}
     * @return the persisted {@link FileUpload}
     * @throws org.springframework.web.server.ResponseStatusException 413 when it is
     *         too large, 415 when the bytes are not an allowed document format
     */
    FileUpload uploadDocument(MultipartFile file, String folder);

    /**
     * Resolves the browser-facing URL for a public object name.
     *
     * <p>Only valid for objects in the public bucket. Prefer
     * {@link #getPreviewUrl(FileUpload)} whenever the entity is at hand — it picks
     * the right scheme for the file's visibility instead of assuming.
     *
     * @param objectName the storage object name (key) of the file
     * @return a permanent, unsigned URL
     */
    String getPreviewUrl(String objectName);

    /**
     * Resolves the browser-facing URL for a file, signing it when the file is
     * private.
     *
     * @param file the stored file; may be null
     * @return a URL that can be used to preview/access the file, or null when
     *         {@code file} is null
     */
    String getPreviewUrl(FileUpload file);

    /**
     * Signs a time-limited download URL for an object in the private bucket.
     *
     * <p>Callers are responsible for authorising the viewer first: this returns a
     * working link to anybody who asks. It never touches the database, so it is
     * safe to call once per document while rendering a page.
     *
     * @param objectName the storage object name (key) of a private object
     * @return a presigned URL valid for the configured expiry window
     */
    String getPrivateUrl(String objectName);

    /**
     * Retrieves public file metadata by its stored object name.
     *
     * @param name the storage object name (key) of the file
     * @return the matching file metadata as a {@link FileUploadResponse}
     * @throws org.springframework.web.server.ResponseStatusException 404 when the
     *         object is unknown <em>or</em> private — this backs an anonymous
     *         endpoint, so it must not confirm that a document exists
     */
    FileUploadResponse getByName(String name);

    /**
     * Resolves an object name supplied by a client, confirming the file exists and
     * that this caller uploaded it. Use before attaching a client-provided object
     * name to an entity, otherwise a caller can claim somebody else's upload.
     *
     * <p>Files stored before uploader auditing existed carry no owner and are
     * accepted with a warning, so historical rows keep working.
     *
     * @param objectName the storage object name (key) the caller claims
     * @param ownerId    the Keycloak subject that must own the file
     * @return the owned {@link FileUpload}
     * @throws org.springframework.web.server.ResponseStatusException 404 when the
     *         object is unknown, 403 when it belongs to somebody else
     */
    FileUpload requireOwnedFile(String objectName, String ownerId);

    /**
     * Batch form of {@link #requireOwnedFile(String, String)}, applying the same
     * rule to every name in one query rather than one query per name.
     *
     * @param objectNames the storage object names the caller claims
     * @param ownerId     the Keycloak subject that must own every one of them
     * @return the owned files, in no particular order
     * @throws org.springframework.web.server.ResponseStatusException 400 listing
     *         every unknown name, 403 listing every name owned by somebody else
     */
    List<FileUpload> requireOwnedFiles(Collection<String> objectNames, String ownerId);

    /**
     * Deletes a file from object storage and removes its associated metadata.
     *
     * <p>Only the uploader or an admin may delete a file. Without that check any
     * caller allowed to reach the endpoint could delete another seller's listing
     * images by object name.
     *
     * @param name the storage object name (key) of the file to delete
     * @throws org.springframework.web.server.ResponseStatusException 404 when the
     *         object is unknown, 403 when the caller did not upload it
     */
    void delete(String name);

    /**
     * Best-effort cleanup used when a file is being replaced. Unlike
     * {@link #delete(String)} it never throws and does not re-check ownership — the
     * caller has already established the right to replace the file — so a storage
     * hiccup cannot roll back the business change that made the old file obsolete.
     *
     * @param file the file to remove; ignored when {@code null}
     */
    void deleteQuietly(FileUpload file);

    /**
     * Uploads several images in one request, applying the same validation to each.
     *
     * @param files the multipart images to upload
     * @return one response per file, in request order
     */
    List<FileUploadResponse> uploadMultiple(List<MultipartFile> files);
}
