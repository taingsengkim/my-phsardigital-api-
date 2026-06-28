package co.istad.sengkim.phsardigital.features.file;

import co.istad.sengkim.phsardigital.core.event.FileDeletedEvent;
import co.istad.sengkim.phsardigital.features.file.dto.FileUploadResponse;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {
    private final MinioClient minioClient;
    private final FileUploadRepository fileUploadRepository;
    private final FileUploadMapper fileUploadMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${minio.bucket}")
    private String bucket;

    /**
     * @implNote: If the bucket is private, create a method in your service
     */
    @Override
    public String getPreviewUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(60 * 60)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FileUploadResponse upload(MultipartFile file) {
        try {
            String objectName = UUID.randomUUID() + "-" + file.getOriginalFilename();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
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
}