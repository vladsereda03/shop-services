package shop.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shop.product.model.Good;

public interface GoodRepository extends JpaRepository<Good, Long> {
}
