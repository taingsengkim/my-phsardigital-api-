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

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioConfig {

    private final MinioProps props;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(props.getUrl())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }

    /**
     * Creates the bucket when missing and re-applies the anonymous read policy on
     * every boot, so the plain object URLs handed to the browser resolve without a
     * presigned token. The policy is applied to pre-existing buckets too: a bucket
     * created before this API ever ran would otherwise stay private and answer 403
     * to every image request. A storage outage only logs here — it must not stop
     * the API from starting.
     */
    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient) {
        return args -> {
            String bucket = props.getBucket();
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

                if (props.isPublicRead()) {
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(publicReadPolicy(bucket))
                            .build());
                    log.info("Applied anonymous read policy to MinIO bucket '{}'", bucket);
                }
            } catch (Exception exception) {
                log.warn("Could not prepare MinIO bucket '{}'. Image URLs may return 403.",
                        bucket, exception);
            }
        };
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
