package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {
    FileUploadResponse upload(MultipartFile file);
    String getPreviewUrl(String objectName);

    FileUploadResponse getByName(String name);
    void delete(String name);
}
