package shop.cart.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import shop.cart.model.Cart;
import shop.cart.model.dto.ProductDTO;
import shop.cart.repository.CartRepository;
import shop.event.UserRegisteredEvent;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final Logger logger = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;

    private final OAuth2AuthorizedClientManager clientManager;
    private final RestClient restClient;

    //todo: design this method!!
    public ProductDTO getProduct(Long id) {
        return restClient.get()
                .uri("http://product.local:8081/api/products/{id}", id)
                .retrieve()
                .body(ProductDTO.class);
    }


    @KafkaListener(topics = "user-registered-events-topic")
    public void createCart(UserRegisteredEvent event) {
        System.out.println("*************************");
        Cart cart = Cart.builder().userId(event.getUserId()).build();
        cartRepository.saveAndFlush(cart);
        logger.info("Cart saved for user {}", event.getUsername());
    }

    public void refreshCart(Cart cart) {

    }
}
