package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.config.config.MinioProps;
import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLConnection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {
    private final MinioClient minioClient;
    private final FileUploadRepository fileUploadRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MinioProps minioProps;

    @Override
    public String getPreviewUrl(String objectName) {
        return minioProps.publicBaseUrl() + minioProps.getBucket() + "/" + objectName;
    }

    @Override
    public FileUploadResponse upload(MultipartFile file) {
        FileUpload stored = store(file, null, resolveContentType(file));
        return new FileUploadResponse(stored.getObjectName(), getPreviewUrl(stored.getObjectName()));
    }

    @Override
    public FileUpload uploadImage(MultipartFile file, String folder) {
        String contentType = resolveContentType(file);
        long maxBytes = minioProps.getMaxImageSize().toBytes();

        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image must not exceed " + minioProps.getMaxImageSize().toMegabytes() + " MB."
            );
        }
        if (!minioProps.getAllowedImageTypes().contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported image type. Allowed types: "
                            + String.join(", ", minioProps.getAllowedImageTypes())
            );
        }
        return store(file, folder, contentType);
    }

    @Override
    public FileUploadResponse getByName(String name) {
        FileUpload file = fileUploadRepository.findByObjectName(name)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "File not found!"));
        return new FileUploadResponse(
                file.getObjectName(),
                getPreviewUrl(file.getObjectName())
        );
    }

    @Override
    @Transactional
    public void delete(String name) {
        FileUpload file = fileUploadRepository.findByObjectName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        try {
            removeObject(file.getObjectName());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to remove from MinIO: " + e.getMessage());
        }
        // clear references BEFORE deleting the row, so the FK constraint doesn't trip
        eventPublisher.publishEvent(new FileDeletedEvent(file.getId(), file.getObjectName()));
        fileUploadRepository.delete(file);
    }

    @Override
    @Transactional
    public void deleteQuietly(FileUpload file) {
        if (file == null) {
            return;
        }
        try {
            removeObject(file.getObjectName());
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

    @Override
    public List<FileUploadResponse> uploadMultiple(List<MultipartFile> files) {
        return files.stream().map(this::upload).toList();
    }

    /**
     * Streams the file into the bucket and records its metadata. The content type
     * written to storage and the one written to the database are always the same
     * value, so a preview URL never contradicts the stored metadata.
     */
    private FileUpload store(MultipartFile file, String folder, String contentType) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty.");
        }
        String objectName = buildObjectName(folder, file.getOriginalFilename());
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProps.getBucket())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
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
        return fileUploadRepository.save(entity);
    }

    private void removeObject(String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(minioProps.getBucket())
                        .object(objectName)
                        .build()
        );
    }

    private String resolveContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && !ct.isBlank()
                && !ct.equals("application/octet-stream")
                && !ct.equals("application/json")) {
            return ct;
        }
        String guessed = URLConnection.guessContentTypeFromName(file.getOriginalFilename());
        return guessed != null ? guessed : "application/octet-stream";
    }

    /**
     * Builds a safe object name: optional folder prefix + random UUID + sanitized
     * original filename. Strips spaces and special characters that break URLs in
     * the browser.
     */
    private String buildObjectName(String folder, String originalFilename) {
        String safeName = (originalFilename == null || originalFilename.isBlank())
                ? "file"
                : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = UUID.randomUUID() + "-" + safeName;
        return (folder == null || folder.isBlank()) ? key : folder.strip() + "/" + key;
    }
}
