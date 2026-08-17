package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.config.config.MinioConfig;
import co.istad.projectpracticum.phsardigital.config.config.MinioProps;
import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.auth.RoleEnum;
import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    /**
     * Caps a single batch request. Without it one call can create dozens of objects
     * and rows, and the servlet request-size limit is a poor proxy for that.
     */
    private static final int MAX_BATCH_SIZE = 10;

    private final MinioClient minioClient;
    private final MinioClient presignMinioClient;
    private final FileUploadRepository fileUploadRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MinioProps minioProps;

    /**
     * Written out rather than generated: two {@link MinioClient} beans are in play
     * and Lombok does not carry {@code @Qualifier} onto the generated constructor,
     * so the injection point has to name which one it wants.
     */
    public FileUploadServiceImpl(MinioClient minioClient,
                                 @Qualifier(MinioConfig.PRESIGN_CLIENT) MinioClient presignMinioClient,
                                 FileUploadRepository fileUploadRepository,
                                 ApplicationEventPublisher eventPublisher,
                                 MinioProps minioProps) {
        this.minioClient = minioClient;
        this.presignMinioClient = presignMinioClient;
        this.fileUploadRepository = fileUploadRepository;
        this.eventPublisher = eventPublisher;
        this.minioProps = minioProps;
    }

    @Override
    public String getPreviewUrl(String objectName) {
        return minioProps.publicBaseUrl() + minioProps.getBucket() + "/" + objectName;
    }

    @Override
    public String getPreviewUrl(FileUpload file) {
        if (file == null) {
            return null;
        }
        return visibilityOf(file) == FileVisibility.PRIVATE
                ? getPrivateUrl(file.getObjectName())
                : getPreviewUrl(file.getObjectName());
    }

    @Override
    public String getPrivateUrl(String objectName) {
        try {
            return presignMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProps.getPrivateBucket())
                            .object(objectName)
                            .expiry((int) minioProps.getPresignedUrlExpiry().toSeconds())
                            .build()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not create a download link for " + objectName, exception);
        }
    }

    @Override
    public FileUploadResponse upload(MultipartFile file) {
        FileUpload stored = uploadImage(file, null);
        return new FileUploadResponse(stored.getObjectName(), getPreviewUrl(stored));
    }

    @Override
    public FileUpload uploadImage(MultipartFile file, String folder) {
        String contentType = validate(
                file, minioProps.getMaxImageSize().toBytes(),
                minioProps.getMaxImageSize().toMegabytes(),
                minioProps.getAllowedImageTypes(), "image");

        return store(file, folder, contentType,
                minioProps.getBucket(), FileVisibility.PUBLIC);
    }

    @Override
    public FileUpload uploadDocument(MultipartFile file, String folder) {
        String contentType = validate(
                file, minioProps.getMaxDocumentSize().toBytes(),
                minioProps.getMaxDocumentSize().toMegabytes(),
                minioProps.getAllowedDocumentTypes(), "document");

        return store(file, folder, contentType,
                minioProps.getPrivateBucket(), FileVisibility.PRIVATE);
    }

    @Override
    public List<FileUploadResponse> uploadMultiple(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files were sent.");
        }
        if (files.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload at most " + MAX_BATCH_SIZE + " files at a time.");
        }
        return files.stream().map(this::upload).toList();
    }

    @Override
    public FileUploadResponse getByName(String name) {
        FileUpload file = findOr404(name);
        if (visibilityOf(file) == FileVisibility.PRIVATE) {
            // This backs an anonymous endpoint. Answering anything other than 404
            // would confirm that somebody's identity document exists.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found!");
        }
        return new FileUploadResponse(file.getObjectName(), getPreviewUrl(file.getObjectName()));
    }

    @Override
    public FileUpload requireOwnedFile(String objectName, String ownerId) {
        FileUpload file = fileUploadRepository.findByObjectName(objectName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown file: " + objectName + ". Please upload it first."));

        if (!isOwnedBy(file, ownerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "That file was uploaded by somebody else.");
        }
        return file;
    }

    @Override
    public List<FileUpload> requireOwnedFiles(Collection<String> objectNames, String ownerId) {
        if (objectNames == null || objectNames.isEmpty()) {
            return List.of();
        }
        List<FileUpload> found = fileUploadRepository.findAllByObjectNameIn(objectNames);

        Set<String> foundNames = found.stream()
                .map(FileUpload::getObjectName)
                .collect(Collectors.toSet());
        List<String> missing = objectNames.stream()
                .filter(name -> !foundNames.contains(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The following files were not found, please upload them first: " + missing);
        }

        List<String> notOwned = found.stream()
                .filter(file -> !isOwnedBy(file, ownerId))
                .map(FileUpload::getObjectName)
                .toList();
        if (!notOwned.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The following files were uploaded by somebody else: " + notOwned);
        }
        return found;
    }

    /**
     * The single definition of "this caller may use this file". Files stored before
     * uploader auditing existed carry no owner and are accepted with a warning, so
     * historical rows keep working.
     */
    private boolean isOwnedBy(FileUpload file, String ownerId) {
        String uploader = file.getCreatedBy();
        if (uploader == null) {
            log.warn("File '{}' has no recorded uploader; allowing {} to claim it",
                    file.getObjectName(), ownerId);
            return true;
        }
        return uploader.equals(ownerId);
    }

    @Override
    @Transactional
    public void delete(String name) {
        // An admin moderating content may remove anything; everybody else is held
        // to what they uploaded. Skipping this let any caller who could reach the
        // endpoint delete another seller's images by guessing nothing at all — the
        // object names are handed out in every listing response.
        FileUpload file = AuthUtils.hasRole(RoleEnum.ADMIN.name())
                ? findOr404(name)
                : requireOwnedFile(name, AuthUtils.extractUserId());

        // Clear references and drop the row first. If that fails the object is
        // still reachable; doing it the other way round leaves a row pointing at
        // an object that no longer exists.
        eventPublisher.publishEvent(new FileDeletedEvent(file.getId(), file.getObjectName()));
        fileUploadRepository.delete(file);

        try {
            removeObject(file);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to remove from MinIO: " + exception.getMessage(), exception);
        }
    }

    @Override
    @Transactional
    public void deleteQuietly(FileUpload file) {
        if (file == null) {
            return;
        }
        try {
            removeObject(file);
        } catch (Exception exception) {
            // The row is still dropped: a leftover object is cheaper than a dangling reference.
            log.warn("Could not remove object '{}' from MinIO", file.getObjectName(), exception);
        }
        try {
            eventPublisher.publishEvent(new FileDeletedEvent(file.getId(), file.getObjectName()));
            fileUploadRepository.delete(file);
        } catch (RuntimeException exception) {
            log.warn("Could not delete file metadata for '{}'", file.getObjectName(), exception);
        }
    }

    /**
     * Rejects the upload unless it is non-empty, within {@code maxBytes}, and its
     * leading bytes identify it as one of {@code allowedTypes}.
     *
     * @return the detected content type, which is what gets stored — never the type
     *         the client declared, since that is theirs to choose freely
     */
    private String validate(MultipartFile file, long maxBytes, long maxMegabytes,
                            Collection<String> allowedTypes, String label) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The " + label + " must not be empty.");
        }
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    "The " + label + " must not exceed " + maxMegabytes + " MB.");
        }

        String detected = FileTypeDetector.detect(file);
        if (detected == null || !containsIgnoringCase(allowedTypes, detected)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "That file is not a supported " + label + ". Allowed types: "
                            + String.join(", ", allowedTypes));
        }
        return detected;
    }

    private boolean containsIgnoringCase(Collection<String> allowedTypes, String detected) {
        return allowedTypes.stream()
                .anyMatch(allowed -> allowed.toLowerCase(Locale.ROOT).equals(detected));
    }

    /**
     * Streams the file into the given bucket and records its metadata. The content
     * type written to storage and the one written to the database are always the
     * same detected value, so a preview URL never contradicts the stored metadata
     * and storage can never be made to serve a file back as something it is not.
     */
    private FileUpload store(MultipartFile file, String folder, String contentType,
                             String bucket, FileVisibility visibility) {
        String objectName = buildObjectName(folder, file.getOriginalFilename());
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
                            .extraHeaders(contentDisposition(contentType, objectName))
                            .build()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed : " + e.getMessage());
        }

        FileUpload entity = new FileUpload();
        entity.setObjectName(objectName);
        entity.setOriginalName(file.getOriginalFilename());
        entity.setContentType(contentType);
        entity.setSize(file.getSize());
        entity.setBucket(bucket);
        entity.setVisibility(visibility);
        return fileUploadRepository.save(entity);
    }

    /**
     * Images render in place; anything else is forced to download. Storage serves
     * these objects from its own origin, so telling the browser not to render a
     * non-image there keeps a document from ever executing as a page.
     */
    private Multimap<String, String> contentDisposition(String contentType, String objectName) {
        String disposition = contentType.startsWith("image/") ? "inline" : "attachment";
        Multimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Disposition",
                disposition + "; filename=\"" + fileNameOf(objectName) + "\"");
        return headers;
    }

    private String fileNameOf(String objectName) {
        int lastSlash = objectName.lastIndexOf('/');
        return lastSlash < 0 ? objectName : objectName.substring(lastSlash + 1);
    }

    private FileUpload findOr404(String name) {
        return fileUploadRepository.findByObjectName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found!"));
    }

    private void removeObject(FileUpload file) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketOf(file))
                        .object(file.getObjectName())
                        .build()
        );
    }

    /**
     * Rows written before private storage existed carry no bucket or visibility;
     * every one of them is a public image.
     */
    private String bucketOf(FileUpload file) {
        return file.getBucket() == null || file.getBucket().isBlank()
                ? minioProps.getBucket()
                : file.getBucket();
    }

    private FileVisibility visibilityOf(FileUpload file) {
        return file.getVisibility() == null ? FileVisibility.PUBLIC : file.getVisibility();
    }

    /**
     * Builds a safe object name: optional folder prefix + random UUID + sanitized
     * original filename. Strips spaces and special characters that break URLs in
     * the browser, and caps the length so a pathological filename cannot overflow
     * the column.
     */
    private String buildObjectName(String folder, String originalFilename) {
        String safeName = (originalFilename == null || originalFilename.isBlank())
                ? "file"
                : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.length() > 100) {
            safeName = safeName.substring(safeName.length() - 100);
        }
        String key = UUID.randomUUID() + "-" + safeName;
        return (folder == null || folder.isBlank()) ? key : folder.strip() + "/" + key;
    }
}
