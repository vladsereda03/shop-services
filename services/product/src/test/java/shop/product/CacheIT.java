package shop.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Proves the catalog cache is genuinely Redis-backed: Boot builds a RedisCacheManager from
// CacheConfig, a value round-trips through the cache abstraction, and the entry really materializes
// as a key in Redis. Full context (Postgres + MinIO) because @SpringBootTest boots the whole
// product app; Redis is wired through @DynamicPropertySource.
@SpringBootTest
@Testcontainers
class CacheIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-01-20T14-49-07Z");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.storage.endpoint", minio::getS3URL);
    registry.add("app.storage.access-key", minio::getUserName);
    registry.add("app.storage.secret-key", minio::getPassword);
    registry.add("app.storage.public-base-url", () -> minio.getS3URL() + "/product-images");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired private CacheManager cacheManager;

  @Autowired private StringRedisTemplate redisTemplate;

  @Test
  void catalogCacheIsRedisBackedAndRoundTripsValues() {
    assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);

    Cache cache = cacheManager.getCache("catalog");
    assertThat(cache).isNotNull();
    cache.put("k", "cached-value");

    // read back through the cache abstraction
    assertThat(cache.get("k", String.class)).isEqualTo("cached-value");
    // and the entry really lives in Redis, under the manager's default `<cacheName>::` key prefix
    assertThat(redisTemplate.hasKey("catalog::k")).isTrue();
  }
}
