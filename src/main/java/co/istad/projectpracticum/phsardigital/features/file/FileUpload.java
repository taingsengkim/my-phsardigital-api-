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

    /**
     * Which bucket the object was actually written to. Recorded rather than
     * inferred so a delete always reaches the right bucket, even after the
     * configured bucket names change. Null on rows written before private storage
     * existed — {@link FileUploadServiceImpl} reads those as the public bucket.
     */
    private String bucket;

    /**
     * Whether the object is anonymously readable. Drives how its URL is built, so
     * a private document can never be handed out as a permanent public link. Null
     * on legacy rows, which are all public.
     */
    @Enumerated(EnumType.STRING)
    private FileVisibility visibility;
}
