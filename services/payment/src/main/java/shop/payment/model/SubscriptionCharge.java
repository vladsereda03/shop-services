package shop.payment.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.domain.Persistable;

// idempotency guard for scheduled (emulated) recurring charges: one row per (subscription,
// period), keyed `<subscriptionId>#<periodKey>`. The primary key turns a second tick for the
// same billing period into a failed INSERT instead of a duplicate order. The LiqPay callback
// path keys on payment_id (processed_callback) instead; this covers the local schedule
// emulator, which has no payment_id to deduplicate on.
@Access(AccessType.FIELD)
@Entity
@NoArgsConstructor
@Getter
public class SubscriptionCharge implements Persistable<String> {

  @Id
  @Column(columnDefinition = "varchar(255)")
  private String id;

  @Column(nullable = false)
  private Instant chargedAt;

  public SubscriptionCharge(String id) {
    this.id = id;
    this.chargedAt = Instant.now();
  }

  @Override
  public String getId() {
    return id;
  }

  // always "new": forces persist (a hard INSERT). With the id pre-assigned, save() would
  // otherwise merge — and merge silently UPDATEs a row a concurrent duplicate has just
  // committed, defeating the primary-key guard.
  @Override
  public boolean isNew() {
    return true;
  }
}
