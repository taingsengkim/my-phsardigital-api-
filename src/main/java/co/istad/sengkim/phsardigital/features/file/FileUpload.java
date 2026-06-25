package co.istad.sengkim.phsardigital.features.file;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "files")
@Setter
@Getter
public class FileUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String objectName;
    private String originalName;
    private String contentType;
    private Long size;
}