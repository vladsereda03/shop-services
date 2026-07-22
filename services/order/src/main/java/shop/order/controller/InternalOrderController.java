package shop.order.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shop.order.model.Order;
import shop.order.model.dto.OrderDTO;
import shop.order.model.dto.OrderItemDTO;
import shop.order.service.OrderService;

// service-to-service API: called by payment after a confirmed LiqPay payment
@RestController
@AllArgsConstructor
@RequestMapping("/internal/orders")
public class InternalOrderController {

  private final OrderService orderService;

  // paymentId is the LiqPay idempotency key; optional so the demo checkout and older callers still
  // work (a null key means the order is never deduplicated).
  @PostMapping("/{userId}/checkout")
  public OrderDTO checkout(
      @PathVariable Long userId, @RequestParam(required = false) Long paymentId) {
    return OrderDTO.from(orderService.checkout(userId, paymentId));
  }

  // order from an items snapshot, bypassing the cart (recurring subscription charge)
  @PostMapping("/{userId}")
  public OrderDTO createFromItems(
      @PathVariable Long userId,
      @RequestBody List<OrderItemDTO> items,
      @RequestParam(required = false) Long paymentId) {
    Map<Long, Order.OrderItem> orderItems =
        items.stream()
            .collect(
                Collectors.toMap(
                    OrderItemDTO::getGoodId,
                    item -> new Order.OrderItem(item.getQuantity(), item.getPriceKopeck())));
    return OrderDTO.from(orderService.createOrder(userId, orderItems, paymentId));
  }
}
