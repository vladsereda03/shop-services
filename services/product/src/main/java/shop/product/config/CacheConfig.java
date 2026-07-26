package shop.product.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

// Redis-backed cache (Р5): the catalog read path is cached so repeated GETs skip PostgreSQL. Boot's
// RedisCacheManager auto-configuration adopts this bean as the default configuration for every
// cache. JSON values (human-readable in redis-cli and portable across restarts / instances, unlike
// JDK serialization) and a bounded TTL, so stale entries expire even if an eviction is ever missed.
@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  RedisCacheConfiguration cacheConfiguration(@Value("${app.cache.catalog-ttl:10m}") Duration ttl) {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(ttl)
        .disableCachingNullValues()
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()));
  }
}
