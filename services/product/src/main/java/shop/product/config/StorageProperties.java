package shop.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// object-storage settings (MinIO / any S3). `endpoint` is where product PUTs/GETs; `publicBaseUrl`
// is the browser-reachable prefix the catalog hands out as image URLs (host-published, since the
// browser runs on the host even when the app runs inside compose).
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String endpoint, String accessKey, String secretKey, String bucket, String publicBaseUrl) {}
