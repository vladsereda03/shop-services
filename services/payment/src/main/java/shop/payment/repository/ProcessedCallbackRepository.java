package shop.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shop.payment.model.ProcessedCallback;

public interface ProcessedCallbackRepository extends JpaRepository<ProcessedCallback, Long> {}
