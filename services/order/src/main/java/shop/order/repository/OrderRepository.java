package shop.order.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import shop.order.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  // idempotency lookup: does an order for this confirmed payment already exist?
  Optional<Order> findByPaymentId(Long paymentId);
}
