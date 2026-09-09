package co.istad.projectpracticum.phsardigital.config.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
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

    /**
     * Bucket for anonymously readable objects: avatars, listing images, category
     * icons. Everything in here is world-readable by design.
     */
    private String bucket;

    /**
     * Bucket for objects that must never be anonymously readable — identity
     * documents and business licences attached to seller applications. It carries
     * no bucket policy, so the only way in is a presigned URL.
     */
    private String privateBucket;

    /**
     * Create the buckets at startup when they are missing.
     */
    private boolean autoCreateBucket = true;

    /**
     * Grant anonymous read on every object in {@link #bucket} at startup. The
     * preview URLs handed to the browser are unsigned, so they only resolve while
     * this is enabled. Turn it off only alongside a move to presigned URLs.
     *
     * <p>Never applies to {@link #privateBucket}.
     */
    private boolean publicRead = true;

    private DataSize maxImageSize = DataSize.ofMegabytes(5);

    /**
     * Documents are scans rather than thumbnails, so they get more room than
     * images — but still well under the servlet container's own cap.
     */
    private DataSize maxDocumentSize = DataSize.ofMegabytes(10);

    /**
     * Only formats {@link co.istad.projectpracticum.phsardigital.features.file.FileTypeDetector}
     * can positively identify belong here. Adding a type the detector does not
     * know silently makes it unusable rather than unsafe: every upload of it is
     * rejected.
     */
    private List<String> allowedImageTypes = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    /**
     * A PDF or a photo of the document. SVG and HTML are deliberately absent —
     * both are executable in a browser.
     */
    private List<String> allowedDocumentTypes = List.of(
            "application/pdf", "image/jpeg", "image/png"
    );

    /**
     * A voice note is seconds of speech, not a music file. Ten megabytes is far more
     * than Opus needs for the few minutes a chat message runs to, and small enough that
     * a mistaken upload of somebody's album is refused rather than stored.
     */
    private DataSize maxAudioSize = DataSize.ofMegabytes(10);

    /**
     * What browsers record into: WebM in Chrome and Firefox, MP4 in Safari. Same rule as
     * the lists above — every entry must be one
     * {@link co.istad.projectpracticum.phsardigital.features.file.FileTypeDetector} can
     * positively identify, or uploads of it are simply rejected.
     */
    private List<String> allowedAudioTypes = List.of(
            "audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg"
    );

    /**
     * How long a presigned download link for a private object stays valid. Long
     * enough for an admin to open the document, short enough that a leaked URL in
     * a referrer header or a chat log is worthless by the time anybody finds it.
     * MinIO caps this at seven days.
     */
    private Duration presignedUrlExpiry = Duration.ofMinutes(15);

    /**
     * @return the browser-facing base URL, always with a trailing slash.
     */
    public String publicBaseUrl() {
        String base = (publicUrl == null || publicUrl.isBlank()) ? url : publicUrl;
        return base.endsWith("/") ? base : base + "/";
    }
}
