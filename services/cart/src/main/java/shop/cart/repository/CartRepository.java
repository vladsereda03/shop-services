package shop.cart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import shop.cart.model.Cart;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @Query(value = "SELECT nextval('order_seq')", nativeQuery = true)
    Long getCurrentOrderSeq();

    Optional<Cart> findByUserId(Long userId);
}
