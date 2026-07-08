package shop.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shop.payment.model.Subscription;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // due subscriptions for a scheduled run: started in the past, matching period
    List<Subscription> findByPeriodicityAndStartDateBefore(String periodicity, LocalDateTime startDate);

    List<Subscription> findAllByUserIdOrderByIdDesc(Long userId);
}
