package shop.product;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import shop.product.model.Good;
import shop.product.model.dto.CreateGoodRequest;
import shop.product.repository.GoodRepository;
import shop.product.service.ProductService;

// Proves the catalog cache is genuinely Redis-backed and behaves correctly: Boot builds a
// RedisCacheManager from CacheConfig, values round-trip and materialize as keys in Redis, the
// catalog list is served from cache, a stock change does not evict it, and creating a good does.
// Full context (Postgres + MinIO) because @SpringBootTest boots the whole product app; Redis is
// wired through @DynamicPropertySource.
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

  @Autowired private ProductService productService;

  @Autowired private GoodRepository goodRepository;

  @Autowired private MeterRegistry meterRegistry;

  @BeforeEach
  void reset() {
    goodRepository.deleteAll();
    Cache catalog = cacheManager.getCache("catalog");
    if (catalog != null) {
      catalog.clear();
    }
  }

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

  @Test
  void catalogIsCachedEvictedOnCreateButNotOnStockChange() {
    Good one = new Good("Item one", 1000L, "d", "toys", List.of());
    one.setQuantity(5);
    one = goodRepository.saveAndFlush(one);

    // first read populates the cache
    assertThat(productService.getCatalog()).hasSize(1);

    // a row inserted directly (bypassing @CacheEvict) stays invisible → the list was served cached
    Good two = new Good("Item two", 2000L, "d", "toys", List.of());
    two.setQuantity(5);
    goodRepository.saveAndFlush(two);
    assertThat(productService.getCatalog()).hasSize(1);

    // a stock change must NOT evict the catalog cache (quantity is not in the projection)
    productService.reserve(one.getId(), 1);
    assertThat(productService.getCatalog()).hasSize(1);

    // creating a good through the service evicts the cache → the next read is rebuilt from the DB
    productService.createGood(
        new CreateGoodRequest("Item three", 3000L, "d", "toys", 5, null, List.of()));
    assertThat(productService.getCatalog()).hasSize(3);
  }

  @Test
  void cacheHitsAndMissesAreExportedAsMetrics() {
    goodRepository.saveAndFlush(new Good("Metered", 1000L, "d", "toys", List.of()));

    productService.getCatalog(); // miss: populates the cache
    productService.getCatalog(); // hit: served from Redis

    double misses =
        meterRegistry
            .get("cache.gets")
            .tags("cache", "catalog", "result", "miss")
            .functionCounter()
            .count();
    double hits =
        meterRegistry
            .get("cache.gets")
            .tags("cache", "catalog", "result", "hit")
            .functionCounter()
            .count();

    // RedisCache statistics are enabled and bound to Micrometer, so hits/misses are exported
    // (counters are cumulative across the context, hence >= rather than ==)
    assertThat(misses).isGreaterThanOrEqualTo(1.0);
    assertThat(hits).isGreaterThanOrEqualTo(1.0);
  }
}
