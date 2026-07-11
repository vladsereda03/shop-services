package shop.cart.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.cart.model.Cart;
import shop.cart.model.dto.ProductDTO;
import shop.cart.repository.CartRepository;
import shop.event.UserRegisteredEvent;

@Service
@RequiredArgsConstructor
public class CartService {

  private static final Logger logger = LoggerFactory.getLogger(CartService.class);

  private final CartRepository cartRepository;
  private final RestClient restClient;

  @Value("${services.product.base-url}")
  private String productBaseUrl;

  @Transactional
  public Cart getOrCreateCart(Long userId) {
    return cartRepository
        .findByUserId(userId)
        .orElseGet(() -> cartRepository.saveAndFlush(Cart.builder().userId(userId).build()));
  }

  @Transactional
  public Cart addItem(Long userId, Long goodId, int quantity) {
    if (quantity <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
    }

    ProductDTO product = getProduct(goodId);

    Cart cart = getOrCreateCart(userId);
    cart.addItem(goodId, quantity, product.getPriceKopeck());
    cartRepository.saveAndFlush(cart);

    // reservation goes last: if product-service rejects it (409), the cart change rolls back
    reserveStock(goodId, quantity);
    return cart;
  }

  @Transactional
  public Cart clearCart(Long userId) {
    Cart cart = getOrCreateCart(userId);
    cart.getItems().forEach((goodId, item) -> releaseStock(goodId, item.getQuantity()));
    cart.clearItems();
    return cartRepository.saveAndFlush(cart);
  }

  // checkout: the goods are sold, so the reserved stock must NOT go back to the shelf
  @Transactional
  public Cart clearAfterCheckout(Long userId) {
    Cart cart = getOrCreateCart(userId);
    cart.clearItems();
    return cartRepository.saveAndFlush(cart);
  }

  public ProductDTO getProduct(Long id) {
    return restClient
        .get()
        .uri(productBaseUrl + "/api/products/{id}", id)
        .retrieve()
        .body(ProductDTO.class);
  }

  private void reserveStock(Long goodId, int quantity) {
    restClient
        .post()
        .uri(productBaseUrl + "/api/products/{id}/reserve?quantity={quantity}", goodId, quantity)
        .retrieve()
        .toBodilessEntity();
  }

  private void releaseStock(Long goodId, int quantity) {
    restClient
        .post()
        .uri(productBaseUrl + "/api/products/{id}/release?quantity={quantity}", goodId, quantity)
        .retrieve()
        .toBodilessEntity();
  }

  @KafkaListener(topics = "user-registered-events-topic")
  public void createCart(UserRegisteredEvent event) {
    if (cartRepository.findByUserId(event.getUserId()).isPresent()) {
      logger.info("Cart already exists for user {}", event.getUsername());
      return;
    }
    Cart cart = Cart.builder().userId(event.getUserId()).build();
    cartRepository.saveAndFlush(cart);
    logger.info("Cart created for user {}", event.getUsername());
  }
}
