package shop.order.controller;

import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import shop.order.model.dto.OrderDTO;
import shop.order.service.OrderService;
import shop.order.service.OrderStreamService;

@RestController
@AllArgsConstructor
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;
  private final OrderStreamService orderStreamService;

  @GetMapping("/my")
  public List<OrderDTO> myOrders(@AuthenticationPrincipal Jwt jwt) {
    return orderService.getMyOrders(currentUserId(jwt)).stream().map(OrderDTO::from).toList();
  }

  // live feed of the caller's orders as they are committed (SSE). A reactive Flux streamed over the
  // servlet stack; the browser consumes it with EventSource. New orders arrive from async sources —
  // a LiqPay callback or a scheduled recurring charge — without the page polling or refreshing.
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<OrderDTO> stream(@AuthenticationPrincipal Jwt jwt) {
    return orderStreamService.streamFor(currentUserId(jwt));
  }

  // checkout: turn the current cart into an order and clear the cart. The demo (no-payment) path
  // carries no idempotency key — each manual checkout is a distinct order.
  @PostMapping("/my")
  public OrderDTO checkout(@AuthenticationPrincipal Jwt jwt) {
    return OrderDTO.from(orderService.checkout(currentUserId(jwt), null));
  }

  private Long currentUserId(Jwt jwt) {
    Object uid = jwt.getClaim("uid");
    if (uid instanceof Number number) {
      return number.longValue();
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access token has no uid claim");
  }
}
