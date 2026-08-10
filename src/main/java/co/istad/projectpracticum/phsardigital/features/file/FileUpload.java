package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Extends {@link BasedEntity} so auditing records which Keycloak subject uploaded
 * the object. Without that, nothing can tell whether a caller attaching an object
 * name actually owns the file.
 */
@Entity
@Table(name = "files")
@Setter
@Getter
public class FileUpload extends BasedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String objectName;
    private String originalName;
    private String contentType;
    private Long size;
}
