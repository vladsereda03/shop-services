package shop.payment.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.model.dto.OrderItemDTO;

// the HTTP edge to order-service in its own bean: resilience annotations are AOP
// proxies and would be silently bypassed on self-invocation inside the services.
// deliberately NO @Retry anywhere here: creating orders is not idempotent
@Component
@RequiredArgsConstructor
public class OrderClient {

  private final RestClient restClient;

  @Value("${services.order.base-url}")
  private String orderBaseUrl;

  @CircuitBreaker(name = "order", fallbackMethod = "checkoutUnavailable")
  public void checkout(Long userId) {
    restClient
        .post()
        .uri(orderBaseUrl + "/internal/orders/{userId}/checkout", userId)
        .retrieve()
        .toBodilessEntity();
  }

  @CircuitBreaker(name = "order", fallbackMethod = "createOrderUnavailable")
  public void createOrder(Long userId, List<OrderItemDTO> items) {
    restClient
        .post()
        .uri(orderBaseUrl + "/internal/orders/{userId}", userId)
        .body(items)
        .retrieve()
        .toBodilessEntity();
  }

  // fallbacks are scoped to the OPEN breaker (CallNotPermittedException):
  // real upstream errors keep their semantics, e.g. 400 "cart is empty"
  private void checkoutUnavailable(Long userId, CallNotPermittedException e) {
    throw orderUnavailable();
  }

  private void createOrderUnavailable(
      Long userId, List<OrderItemDTO> items, CallNotPermittedException e) {
    throw orderUnavailable();
  }

  private ResponseStatusException orderUnavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Order service is temporarily unavailable");
  }
}
