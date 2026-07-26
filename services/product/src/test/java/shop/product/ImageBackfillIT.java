package shop.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import shop.product.service.ImageBackfillRunner;
import shop.product.service.ImageStorage;

// Proves the one-time data migration end to end against a real PostgreSQL + MinIO: a legacy row that
// still carries image bytes in the `good.image` bytea column is drained into object storage, and a
// second run is a no-op. The startup pass runs on an empty table (nothing to do); the test seeds a
// legacy row with raw JDBC — the Good entity no longer maps `image`, so it cannot be inserted
// through the repository — and drives the runner directly.
@SpringBootTest
@Testcontainers
class ImageBackfillIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-01-20T14-49-07Z");

  @DynamicPropertySource
  static void storageProperties(DynamicPropertyRegistry registry) {
    registry.add("app.storage.endpoint", minio::getS3URL);
    registry.add("app.storage.access-key", minio::getUserName);
    registry.add("app.storage.secret-key", minio::getPassword);
    registry.add("app.storage.public-base-url", () -> minio.getS3URL() + "/product-images");
  }

  @Autowired private JdbcTemplate jdbc;

  @Autowired private ImageBackfillRunner backfillRunner;

  @Autowired private ImageStorage imageStorage;

  @Test
  void legacyImageBytesMoveToObjectStorageAndTheColumnIsDrained() {
    byte[] legacy = "legacy-image-bytes".getBytes(StandardCharsets.UTF_8);
    jdbc.update(
        "INSERT INTO good (name, description, category, image, price_kopeck, quantity)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        "Legacy good", "d", "c", legacy, 1000L, 3);
    Long id = jdbc.queryForObject("SELECT id FROM good WHERE name = ?", Long.class, "Legacy good");

    backfillRunner.run(null);

    String key = jdbc.queryForObject("SELECT image_key FROM good WHERE id = ?", String.class, id);
    // the row now points at object storage and the bytes round-trip through the real client
    assertThat(key).isNotNull();
    assertThat(imageStorage.get(key)).isEqualTo(legacy);
    // the legacy bytea column has been drained
    assertThat(jdbc.queryForObject("SELECT image FROM good WHERE id = ?", byte[].class, id))
        .isNull();

    // idempotent: the already-migrated row has a key, so a second pass leaves it untouched
    backfillRunner.run(null);
    assertThat(jdbc.queryForObject("SELECT image_key FROM good WHERE id = ?", String.class, id))
        .isEqualTo(key);
  }
}
