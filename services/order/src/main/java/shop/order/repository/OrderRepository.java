package shop.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shop.order.model.Order;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
