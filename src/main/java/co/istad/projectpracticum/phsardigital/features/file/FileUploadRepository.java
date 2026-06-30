package co.istad.projectpracticum.phsardigital.features.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileUploadRepository extends JpaRepository<FileUpload, UUID> {

    Optional<FileUpload> findByObjectName(String objectName);
    boolean existsByObjectName(String objectName);
    // Useful for batch validation in one query instead of N queries
    @Query("SELECT f.objectName FROM FileUpload f WHERE f.objectName IN :objectNames")
    List<String> findExistingObjectNames(@Param("objectNames") Collection<String> objectNames);

    List<FileUpload> findAllByObjectNameIn(Collection<String> objectNames);
}
