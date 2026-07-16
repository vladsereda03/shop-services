package shop.payment.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.domain.Persistable;

// a LiqPay callback that has already been acted upon, keyed by LiqPay's payment_id;
// the primary key is the idempotency guard — a duplicate INSERT fails instead of
// producing a second order
@Access(AccessType.FIELD)
@Entity
@NoArgsConstructor
@Getter
public class ProcessedCallback implements Persistable<Long> {

  @Id
  @Column(columnDefinition = "bigint")
  private Long paymentId;

  @Column(nullable = false)
  private Instant processedAt;

  public ProcessedCallback(long paymentId) {
    this.paymentId = paymentId;
    this.processedAt = Instant.now();
  }

  @Override
  public Long getId() {
    return paymentId;
  }

  // always "new": forces persist (a hard INSERT). With the id pre-assigned, save()
  // would otherwise merge — and merge silently UPDATEs a row a concurrent duplicate
  // has just committed, defeating the primary-key guard
  @Override
  public boolean isNew() {
    return true;
  }
}
