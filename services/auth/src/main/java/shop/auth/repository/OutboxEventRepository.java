package shop.auth.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shop.auth.model.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  // claim a batch of unpublished events oldest-first. FOR UPDATE SKIP LOCKED lets several
  // instances poll in parallel without ever handing the same row to two publishers.
  @Query(
      value =
          "SELECT * FROM outbox WHERE published_at IS NULL "
              + "ORDER BY id LIMIT :batchSize FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);

  // creation time of the oldest still-unpublished event, or null when the outbox is drained;
  // drives the outbox-lag gauge. Same predicate as the poll query, so the partial index applies.
  @Query("SELECT MIN(o.createdAt) FROM OutboxEvent o WHERE o.publishedAt IS NULL")
  Instant findOldestUnpublishedCreatedAt();
}
