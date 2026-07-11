package shop.order.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.order.model.Order;
import shop.order.model.dto.CartDTO;
import shop.order.model.dto.CartItemDTO;
import shop.order.repository.OrderRepository;

@Service
@RequiredArgsConstructor
public class OrderService {

  private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository orderRepository;
  private final RestClient restClient;

  @Value("${services.cart.base-url}")
  private String cartBaseUrl;

  @Transactional
  public Order checkout(Long userId) {
    CartDTO cart =
        restClient
            .get()
            .uri(cartBaseUrl + "/internal/carts/{userId}", userId)
            .retrieve()
            .body(CartDTO.class);

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

    Order order = createOrder(userId, items);

    // clearing goes last: if it fails, the transaction rolls the new order back
    // (checkout-clear keeps the stock reserved — the goods are sold, not returned)
    restClient
        .post()
        .uri(cartBaseUrl + "/internal/carts/{userId}/checkout-clear", userId)
        .retrieve()
        .toBodilessEntity();

    return order;
  }

  @Transactional
  public Order createOrder(Long userId, Map<Long, Order.OrderItem> items) {
    if (items == null || items.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot create an order from an empty cart");
    }

    Order order =
        Order.builder().userId(userId).createdAt(Instant.now()).items(new HashMap<>(items)).build();

    order = orderRepository.saveAndFlush(order);
    logger.info("Order {} created for user {} ({} items)", order.getId(), userId, items.size());
    return order;
  }

  public List<Order> getMyOrders(Long userId) {
    return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
  }
}
