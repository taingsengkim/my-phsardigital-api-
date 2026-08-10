package co.istad.projectpracticum.phsardigital.config.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "minio")
@Setter
@Getter
@NoArgsConstructor
public class MinioProps {

    /**
     * Endpoint the API talks to. Inside Docker this is usually the service name.
     */
    private String url;

    /**
     * Endpoint the browser talks to. Differs from {@link #url} whenever the API
     * reaches MinIO over an internal network the client cannot resolve.
     */
    private String publicUrl;

    private String accessKey;

    private String secretKey;

    private String bucket;

    /**
     * Create the bucket at startup when it is missing.
     */
    private boolean autoCreateBucket = true;

    private DataSize maxImageSize = DataSize.ofMegabytes(5);

    private List<String> allowedImageTypes = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    /**
     * @return the browser-facing base URL, always with a trailing slash.
     */
    public String publicBaseUrl() {
        String base = (publicUrl == null || publicUrl.isBlank()) ? url : publicUrl;
        return base.endsWith("/") ? base : base + "/";
    }
}
