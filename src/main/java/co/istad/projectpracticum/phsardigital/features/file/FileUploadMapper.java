package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")

public interface FileUploadMapper   {
    FileUploadResponse mapFileUploadToFileUploadResponse(FileUpload fileUpload);
}
