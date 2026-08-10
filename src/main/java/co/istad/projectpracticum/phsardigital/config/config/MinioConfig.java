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
     * Creates the bucket on first boot and opens it for anonymous reads, so the
     * plain object URLs handed to the browser resolve without a presigned token.
     * A storage outage only logs here: it must not stop the API from starting.
     */
    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient) {
        return args -> {
            if (!props.isAutoCreateBucket()) {
                return;
            }
            String bucket = props.getBucket();
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (exists) {
                    return;
                }
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(publicReadPolicy(bucket))
                        .build());
                log.info("Created MinIO bucket '{}' with public read access", bucket);
            } catch (Exception exception) {
                log.warn("Could not prepare MinIO bucket '{}'. Uploads will fail until it exists.",
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
