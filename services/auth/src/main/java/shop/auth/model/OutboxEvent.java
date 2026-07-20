package shop.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

// one domain event awaiting (or already done with) delivery. It is written in the same
// transaction as the state change it describes; OutboxPublisher relays unpublished rows to Kafka.
@Access(AccessType.FIELD)
@Entity
@Table(name = "outbox")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class OutboxEvent {

  @Id
  @Column(columnDefinition = "bigint")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_gen")
  @SequenceGenerator(name = "outbox_gen", sequenceName = "outbox_seq", allocationSize = 1)
  private Long id;

  // the Kafka partition key (here: the user id), which also references the source aggregate
  @Column(nullable = false)
  private String aggregateId;

  // simple class name of the event — metadata for debugging and future multi-type dispatch
  @Column(nullable = false)
  private String eventType;

  @Column(nullable = false)
  private String topic;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String payload;

  @Column(nullable = false)
  private Instant createdAt;

  // null until the broker has acknowledged the send; the poll predicate keys off this column
  private Instant publishedAt;

  public void markPublished() {
    this.publishedAt = Instant.now();
  }
}
