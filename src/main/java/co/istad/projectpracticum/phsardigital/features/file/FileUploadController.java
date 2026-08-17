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

    /** Keeps identity documents under one prefix inside the private bucket. */
    private static final String DOCUMENT_FOLDER = "documents";

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

    /**
     * Uploads a supporting document for a seller application. Kept separate from
     * {@link #upload} because the two have different limits, different accepted
     * formats, and — most of all — different storage: a scan of an identity card
     * must not land in the anonymously readable bucket that serves product images.
     *
     * <p>The returned URL is presigned and expires; re-read the application to get
     * a fresh one.
     */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse uploadDocument(@RequestParam("file") MultipartFile file) {
        FileUpload stored = fileUploadService.uploadDocument(file, DOCUMENT_FOLDER);
        return new FileUploadResponse(stored.getObjectName(), fileUploadService.getPreviewUrl(stored));
    }

    /**
     * Object names contain slashes once a file is stored under a folder prefix, so
     * the trailing {@code {*objectName}} capture is what makes {@code
     * avatars/<uuid>-photo.png} addressable at all. A single {@code {objectName}}
     * segment silently failed to match every foldered file.
     */
    @GetMapping("/preview/{*objectName}")
    public FileUploadResponse preview(@PathVariable String objectName) {
        return fileUploadService.getByName(trimLeadingSlash(objectName));
    }

    @DeleteMapping("/{*objectName}")
    public void deleteFile(@PathVariable String objectName) {
        fileUploadService.delete(trimLeadingSlash(objectName));
    }

    /** A trailing path capture keeps the separator that introduced it. */
    private String trimLeadingSlash(String objectName) {
        return objectName.startsWith("/") ? objectName.substring(1) : objectName;
    }
}
