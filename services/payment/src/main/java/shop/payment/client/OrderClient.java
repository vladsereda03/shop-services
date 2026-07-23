package shop.payment.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import shop.payment.model.dto.OrderItemDTO;

// the HTTP edge to order-service in its own bean: resilience annotations are AOP
// proxies and would be silently bypassed on self-invocation inside the services.
// deliberately NO @Retry anywhere here: creating orders is not idempotent
@Component
public class OrderClient {

  private final RestClient restClient;
  private final String orderBaseUrl;

  public OrderClient(
      RestClient restClient, @Value("${services.order.base-url}") String orderBaseUrl) {
    this.restClient = restClient;
    this.orderBaseUrl = orderBaseUrl;
  }

  @CircuitBreaker(name = "order", fallbackMethod = "checkoutUnavailable")
  public void checkout(Long userId, Long paymentId) {
    restClient
        .post()
        .uri(internalOrderUri("/internal/orders/{userId}/checkout", userId, paymentId))
        .retrieve()
        .toBodilessEntity();
  }

  @CircuitBreaker(name = "order", fallbackMethod = "createOrderUnavailable")
  public void createOrder(Long userId, List<OrderItemDTO> items, Long paymentId) {
    restClient
        .post()
        .uri(internalOrderUri("/internal/orders/{userId}", userId, paymentId))
        .body(items)
        .retrieve()
        .toBodilessEntity();
  }

  // paymentId, when present, travels as a query param so order-service can deduplicate: a
  // redelivered callback resolves to the same order instead of a new one. A null key (recurring
  // charges emulated by the scheduler) is simply omitted.
  private String internalOrderUri(String pathTemplate, Long userId, Long paymentId) {
    return UriComponentsBuilder.fromUriString(orderBaseUrl)
        .path(pathTemplate)
        .queryParamIfPresent("paymentId", Optional.ofNullable(paymentId))
        .buildAndExpand(userId)
        .toUriString();
  }

  // fallbacks are scoped to the OPEN breaker (CallNotPermittedException):
  // real upstream errors keep their semantics, e.g. 400 "cart is empty"
  private void checkoutUnavailable(Long userId, Long paymentId, CallNotPermittedException e) {
    throw orderUnavailable();
  }

  private void createOrderUnavailable(
      Long userId, List<OrderItemDTO> items, Long paymentId, CallNotPermittedException e) {
    throw orderUnavailable();
  }

  private ResponseStatusException orderUnavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Order service is temporarily unavailable");
  }
}
