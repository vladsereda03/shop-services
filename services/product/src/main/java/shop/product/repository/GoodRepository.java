package shop.product.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shop.product.model.Good;

public interface GoodRepository extends JpaRepository<Good, Long> {

  // row lock so that concurrent reserve/release calls cannot oversell stock
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from Good g where g.id = :id")
  Optional<Good> findWithLockById(@Param("id") long id);
}
