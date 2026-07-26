package shop.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shop.payment.model.SubscriptionCharge;

public interface SubscriptionChargeRepository extends JpaRepository<SubscriptionCharge, String> {}
