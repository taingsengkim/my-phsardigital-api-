package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {
    private final MinioClient minioClient;
    private final FileUploadRepository fileUploadRepository;
    private final FileUploadMapper fileUploadMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.url}")
    private String minioUrl;

    /**
     * @implNote If the bucket is private, this presigned URL grants temporary read access.
     */
    @Override
    public String getPreviewUrl(String objectName) {
        String base = minioUrl.endsWith("/") ? minioUrl : minioUrl + "/";
        return base + bucket + "/" + objectName;
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
    @Override
    public FileUploadResponse upload(MultipartFile file) {
        try {
            String objectName = buildObjectName(file.getOriginalFilename());
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(resolveContentType(file))
                            .build()
            );
            FileUpload entity = new FileUpload();
            entity.setObjectName(objectName);
            entity.setOriginalName(file.getOriginalFilename());
            entity.setContentType(file.getContentType());
            entity.setSize(file.getSize());
            fileUploadRepository.save(entity);
            return new FileUploadResponse(objectName, getPreviewUrl(objectName));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed : " + e.getMessage());
        }
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
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(file.getObjectName())
                            .build()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to remove from MinIO: " + e.getMessage());
        }
        // clear references BEFORE deleting the row, so the FK constraint doesn't trip
        eventPublisher.publishEvent(new FileDeletedEvent(file.getId(), file.getObjectName()));
        fileUploadRepository.delete(file);
    }

    @Override
    public List<FileUploadResponse> uploadMultiple(List<MultipartFile> files) {
        return files.stream().map(this::upload).toList();
    }

    /**
     * Builds a safe object name: random UUID + sanitized original filename.
     * Strips spaces and special characters that break presigned URLs in the browser.
     */
    private String buildObjectName(String originalFilename) {
        String safeName = (originalFilename == null || originalFilename.isBlank())
                ? "file"
                : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return UUID.randomUUID() + "-" + safeName;
    }
}