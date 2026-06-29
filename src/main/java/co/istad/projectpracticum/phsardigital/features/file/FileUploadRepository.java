package co.istad.projectpracticum.phsardigital.features.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileUploadRepository extends JpaRepository<FileUpload, UUID> {

    Optional<FileUpload> findByObjectName(String objectName);
}
