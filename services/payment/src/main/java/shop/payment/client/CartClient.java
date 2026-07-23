package shop.payment.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.model.dto.CartDTO;

// the HTTP edge to cart-service in its own bean (see OrderClient for the why)
@Component
public class CartClient {

  private final RestClient restClient;
  private final String cartBaseUrl;

  public CartClient(RestClient restClient, @Value("${services.cart.base-url}") String cartBaseUrl) {
    this.restClient = restClient;
    this.cartBaseUrl = cartBaseUrl;
  }

  // GET is idempotent — safe to retry on transient failures
  @CircuitBreaker(name = "cart", fallbackMethod = "getCartUnavailable")
  @Retry(name = "cart")
  public CartDTO getCart(Long userId) {
    return restClient
        .get()
        .uri(cartBaseUrl + "/internal/carts/{userId}", userId)
        .retrieve()
        .body(CartDTO.class);
  }

  // no @Retry: retries stay on read-only calls, and a failed clear must roll
  // the subscription transaction back immediately
  @CircuitBreaker(name = "cart", fallbackMethod = "clearUnavailable")
  public void clearAfterCheckout(Long userId) {
    restClient
        .post()
        .uri(cartBaseUrl + "/internal/carts/{userId}/checkout-clear", userId)
        .retrieve()
        .toBodilessEntity();
  }

  private CartDTO getCartUnavailable(Long userId, CallNotPermittedException e) {
    throw cartUnavailable();
  }

  private void clearUnavailable(Long userId, CallNotPermittedException e) {
    throw cartUnavailable();
  }

  private ResponseStatusException cartUnavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Cart service is temporarily unavailable");
  }
}
