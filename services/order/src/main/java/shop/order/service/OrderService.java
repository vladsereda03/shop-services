package shop.order.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import shop.order.client.CartClient;
import shop.order.model.Order;
import shop.order.model.dto.CartDTO;
import shop.order.model.dto.CartItemDTO;
import shop.order.repository.OrderRepository;

@Service
@RequiredArgsConstructor
public class OrderService {

  private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository orderRepository;
  private final CartClient cartClient;

  @Transactional
  public Order checkout(Long userId, Long paymentId) {
    // idempotent receiver: a redelivered payment callback must not create a second order. If this
    // payment already produced an order, return it without touching the cart again — the first
    // checkout already cleared it, so a re-pull would see an empty cart and fail.
    Order existing = findByPaymentId(paymentId);
    if (existing != null) {
      logger.info(
          "Order {} already exists for payment {}, skipping checkout", existing.getId(), paymentId);
      return existing;
    }

    CartDTO cart = cartClient.getCart(userId);

    if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot create an order from an empty cart");
    }

    Map<Long, Order.OrderItem> items =
        cart.getItems().stream()
            .collect(
                Collectors.toMap(
                    CartItemDTO::getGoodId,
                    item -> new Order.OrderItem(item.getQuantity(), item.getPriceKopeck())));

    Order order = createOrder(userId, items, paymentId);

    // clearing goes last: if it fails, the transaction rolls the new order back
    // (checkout-clear keeps the stock reserved — the goods are sold, not returned)
    cartClient.clearAfterCheckout(userId);

    return order;
  }

  @Transactional
  public Order createOrder(Long userId, Map<Long, Order.OrderItem> items, Long paymentId) {
    if (items == null || items.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot create an order from an empty cart");
    }

    Order existing = findByPaymentId(paymentId);
    if (existing != null) {
      return existing;
    }

    Order order =
        Order.builder()
            .userId(userId)
            .createdAt(Instant.now())
            .paymentId(paymentId)
            .items(new HashMap<>(items))
            .build();

    // the unique constraint on payment_id is the hard guarantee: even if two callbacks for the
    // same payment slip past the check above concurrently, at most one insert succeeds. The loser
    // fails this call, its transaction rolls back, and LiqPay's next redelivery finds the winner.
    order = orderRepository.saveAndFlush(order);
    logger.info("Order {} created for user {} ({} items)", order.getId(), userId, items.size());
    return order;
  }

  // a null paymentId means "no idempotency key" (recurring charges emulated by the scheduler), so
  // every keyless order is distinct — never dedup those.
  private Order findByPaymentId(Long paymentId) {
    return paymentId == null ? null : orderRepository.findByPaymentId(paymentId).orElse(null);
  }

  public List<Order> getMyOrders(Long userId) {
    return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
  }
}
