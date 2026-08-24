package co.istad.projectpracticum.phsardigital.features.file;

import co.istad.projectpracticum.phsardigital.config.config.MinioProps;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Covers the reported failure: replacing a seller logo whose file is also a listing image. */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceImplReferenceTest {

    private static final String SHARED = "shop/logo-also-used-by-a-listing.jpg";

    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioClient presignMinioClient;
    @Mock
    private FileUploadRepository fileUploadRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MinioProps minioProps;

    @Test
    void replacingALogoKeepsAFileThatAListingStillUses() {
        FileUpload displacedLogo = file(SHARED);

        service(check("a listing image", true, true))
                .deleteQuietly(displacedLogo);

        // The row survives and no FileDeletedEvent goes out to detach it elsewhere.
        verify(fileUploadRepository, never()).delete(any(FileUpload.class));
        verifyNoInteractions(eventPublisher, minioClient);
    }

    @Test
    void replacingALogoStillDeletesAFileNothingElseUses() {
        FileUpload displacedLogo = file("shop/old-logo.jpg");

        service(check("a listing image", false, true)).deleteQuietly(displacedLogo);

        verify(fileUploadRepository).delete(displacedLogo);
    }

    @Test
    void anOptionalReferenceAlsoKeepsTheFile() {
        FileUpload displacedLogo = file(SHARED);

        // Nullable, so it could be detached — but it is still in use.
        service(check("a category icon", true, false)).deleteQuietly(displacedLogo);

        verify(fileUploadRepository, never()).delete(any(FileUpload.class));
    }

    private FileUploadServiceImpl service(FileReferenceCheck... checks) {
        return new FileUploadServiceImpl(
                minioClient,
                presignMinioClient,
                fileUploadRepository,
                eventPublisher,
                minioProps,
                List.of(checks));
    }

    private static FileReferenceCheck check(String name, boolean referenced, boolean mandatory) {
        return new FileReferenceCheck() {
            @Override
            public String referenceName() {
                return name;
            }

            @Override
            public boolean isReferenced(String objectName) {
                return referenced;
            }

            @Override
            public boolean isMandatory() {
                return mandatory;
            }
        };
    }

    private static FileUpload file(String objectName) {
        FileUpload file = new FileUpload();
        file.setId(UUID.randomUUID());
        file.setObjectName(objectName);
        file.setBucket("public");
        file.setVisibility(FileVisibility.PUBLIC);
        return file;
    }

    @Test
    void referenceNamesAreReportedForTheLog() {
        assertThat(check("a listing image", true, true).referenceName())
                .isEqualTo("a listing image");
    }
}
