package shop.order.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import shop.order.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
