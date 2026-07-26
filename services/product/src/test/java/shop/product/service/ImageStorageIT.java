package shop.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import shop.product.config.StorageConfig;
import shop.product.config.StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

// Proves the object-storage wiring end to end against a real MinIO: the same client builder the
// application uses (StorageConfig.buildClient) round-trips bytes through the bucket, and a deleted
// key is gone. No Spring context — this exercises ImageStorage directly.
@Testcontainers
class ImageStorageIT {

  // pinned like the other infra images; predates the community-console removal in later releases
  @Container
  static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-01-20T14-49-07Z");

  private static final String BUCKET = "product-images";

  private ImageStorage newStorage() {
    S3Client s3 =
        StorageConfig.buildClient(minio.getS3URL(), minio.getUserName(), minio.getPassword());
    // the compose stack creates the bucket via a minio/mc init job; the test creates its own
    s3.createBucket(b -> b.bucket(BUCKET));
    StorageProperties props =
        new StorageProperties(
            minio.getS3URL(),
            minio.getUserName(),
            minio.getPassword(),
            BUCKET,
            "http://localhost:9002/" + BUCKET);
    return new ImageStorage(s3, props);
  }

  @Test
  void putThenGetRoundTripsBytesAndDeleteRemovesThem() {
    ImageStorage storage = newStorage();
    byte[] bytes = "hello-minio".getBytes(StandardCharsets.UTF_8);

    String key = storage.put(bytes);

    assertThat(storage.get(key)).isEqualTo(bytes);
    assertThat(storage.urlFor(key)).isEqualTo("http://localhost:9002/" + BUCKET + "/" + key);

    storage.delete(key);
    assertThatThrownBy(() -> storage.get(key)).isInstanceOf(NoSuchKeyException.class);
  }
}
