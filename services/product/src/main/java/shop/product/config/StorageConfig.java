package shop.product.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

  private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

  @Bean
  S3Client s3Client(StorageProperties props) {
    return buildClient(props.endpoint(), props.accessKey(), props.secretKey());
  }

  // ensures the bucket exists and is public-read at startup, so the write path can PUT and the
  // browser can GET image URLs without signing. Retries because compose only waits for MinIO to be
  // "started", not fully ready. @Order(1) so it runs before the legacy image backfill, which PUTs
  // into this bucket.
  @Bean
  @Order(1)
  ApplicationRunner storageInitializer(S3Client s3, StorageProperties props) {
    return args -> {
      SdkException last = null;
      for (int attempt = 1; attempt <= 15; attempt++) {
        try {
          ensureBucket(s3, props.bucket());
          log.info("Object-storage bucket '{}' is ready", props.bucket());
          return;
        } catch (SdkException e) {
          last = e;
          log.info("Object storage not ready (attempt {}/15): {}", attempt, e.getMessage());
          Thread.sleep(1000);
        }
      }
      throw new IllegalStateException("Object storage not reachable after retries", last);
    };
  }

  // shared with ImageStorageIT so the integration test exercises the real client construction.
  // forcePathStyle is required for MinIO (bucket in the path, not a virtual host); the region is
  // ignored by MinIO but the SDK insists on one. The client is lazy — it connects on first use,
  // so the context starts even if MinIO is not up yet.
  public static S3Client buildClient(String endpoint, String accessKey, String secretKey) {
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .build();
  }

  // creates the bucket if missing and applies a public-read policy (anonymous s3:GetObject on the
  // objects). Shared with the integration tests so they exercise the real bootstrap.
  public static void ensureBucket(S3Client s3, String bucket) {
    try {
      s3.headBucket(b -> b.bucket(bucket));
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        s3.createBucket(b -> b.bucket(bucket));
      } else {
        throw e;
      }
    }
    String policy =
        """
        {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*",\
        "Action":"s3:GetObject","Resource":"arn:aws:s3:::%s/*"}]}"""
            .formatted(bucket);
    s3.putBucketPolicy(b -> b.bucket(bucket).policy(policy));
  }
}
