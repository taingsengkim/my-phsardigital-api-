package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse upload(@RequestParam("file") MultipartFile file) {
        return fileUploadService.upload(file);
    }
    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<FileUploadResponse> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files
    ) {
        return fileUploadService.uploadMultiple(files);
    }
    @GetMapping("/{name}/preview")
    public FileUploadResponse preview(@PathVariable String name) {
        return fileUploadService.getByName(name);
    }

    @DeleteMapping("/{name}")
    public void deleteFile(@PathVariable String name) {
        fileUploadService.delete(name);
    }
}