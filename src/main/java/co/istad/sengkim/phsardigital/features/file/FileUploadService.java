package co.istad.sengkim.phsardigital.features.file;

import co.istad.sengkim.phsardigital.features.file.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {
    FileUploadResponse uploadFile(MultipartFile file);
    String getPreviewUrl(String objectName);

    FileUploadResponse getByName(String name);
    void deleteFile(String name);
}
