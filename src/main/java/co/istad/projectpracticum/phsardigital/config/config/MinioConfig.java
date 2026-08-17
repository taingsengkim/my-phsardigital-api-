package co.istad.projectpracticum.phsardigital.config.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioConfig {

    public static final String PRESIGN_CLIENT = "presignMinioClient";

    private final MinioProps props;

    /**
     * The client every server-side operation uses: uploads, deletes, bucket setup.
     * Points at the endpoint the API itself can reach.
     */
    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(props.getUrl())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }

    /**
     * A second client that exists purely to sign download URLs.
     *
     * <p>A presigned URL's signature covers the host it was signed for, so one
     * signed against the internal endpoint is invalid the moment the browser hits
     * the public one — and inside Docker those are different hosts. Signing with a
     * client built on {@link MinioProps#publicBaseUrl()} produces a link that
     * verifies where it is actually used. When both URLs are the same, as in local
     * development, this client is simply a duplicate of the primary one.
     */
    @Bean(PRESIGN_CLIENT)
    public MinioClient presignMinioClient() {
        return MinioClient.builder()
                .endpoint(props.publicBaseUrl())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }

    /**
     * Creates both buckets when missing and re-applies the anonymous read policy to
     * the public one on every boot, so the plain object URLs handed to the browser
     * resolve without a presigned token. The policy is applied to pre-existing
     * buckets too: a bucket created before this API ever ran would otherwise stay
     * private and answer 403 to every image request.
     *
     * <p>The private bucket is created and then left alone — it must keep MinIO's
     * default deny-all policy, which is the whole reason identity documents go
     * there. A storage outage only logs here; it must not stop the API starting.
     */
    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient) {
        return args -> {
            ensureBucket(minioClient, props.getBucket(), props.isPublicRead());
            ensureBucket(minioClient, props.getPrivateBucket(), false);
        };
    }

    private void ensureBucket(MinioClient minioClient, String bucket, boolean anonymousRead) {
        if (bucket == null || bucket.isBlank()) {
            log.warn("No bucket name configured; skipping bucket preparation.");
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());

            if (!exists) {
                if (!props.isAutoCreateBucket()) {
                    log.warn("MinIO bucket '{}' is missing and auto-create is off. Uploads will fail.",
                            bucket);
                    return;
                }
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket '{}'", bucket);
            }

            if (anonymousRead) {
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(publicReadPolicy(bucket))
                        .build());
                log.info("Applied anonymous read policy to MinIO bucket '{}'", bucket);
            }
        } catch (Exception exception) {
            log.warn("Could not prepare MinIO bucket '{}'. Uploads or image URLs may fail.",
                    bucket, exception);
        }
    }

    private String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }
}
