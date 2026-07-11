package shop.cart.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import shop.cart.model.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

  Optional<Cart> findByUserId(Long userId);
}
