package shop.cart.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.cart.model.dto.ProductDTO;

// the HTTP edge to product-service, extracted into its own bean: the resilience
// annotations are AOP proxies, and self-invocation inside CartService would
// silently bypass them
@Component
public class ProductClient {

  private final RestClient restClient;
  private final String productBaseUrl;

  public ProductClient(
      RestClient restClient, @Value("${services.product.base-url}") String productBaseUrl) {
    this.restClient = restClient;
    this.productBaseUrl = productBaseUrl;
  }

  // GET is idempotent — safe to retry on transient failures
  @CircuitBreaker(name = "product", fallbackMethod = "getProductUnavailable")
  @Retry(name = "product")
  public ProductDTO getProduct(Long id) {
    return restClient
        .get()
        .uri(productBaseUrl + "/api/products/{id}", id)
        .retrieve()
        .body(ProductDTO.class);
  }

  // deliberately NO @Retry: reserve/release are not idempotent — a request that
  // succeeded but lost its response would double-book stock on a retry
  @CircuitBreaker(name = "product", fallbackMethod = "reserveUnavailable")
  public void reserveStock(Long goodId, int quantity) {
    restClient
        .post()
        .uri(productBaseUrl + "/api/products/{id}/reserve?quantity={quantity}", goodId, quantity)
        .retrieve()
        .toBodilessEntity();
  }

  @CircuitBreaker(name = "product", fallbackMethod = "releaseUnavailable")
  public void releaseStock(Long goodId, int quantity) {
    restClient
        .post()
        .uri(productBaseUrl + "/api/products/{id}/release?quantity={quantity}", goodId, quantity)
        .retrieve()
        .toBodilessEntity();
  }

  // fallbacks are scoped to the OPEN breaker only (CallNotPermittedException):
  // real upstream errors keep their semantics, e.g. 409 still rolls the cart back
  private ProductDTO getProductUnavailable(Long id, CallNotPermittedException e) {
    throw productUnavailable();
  }

  private void reserveUnavailable(Long goodId, int quantity, CallNotPermittedException e) {
    throw productUnavailable();
  }

  private void releaseUnavailable(Long goodId, int quantity, CallNotPermittedException e) {
    throw productUnavailable();
  }

  private ResponseStatusException productUnavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Product service is temporarily unavailable");
  }
}
