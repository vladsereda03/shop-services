package shop.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shop.auth.model.OutboxEvent;
import shop.auth.repository.OutboxEventRepository;
import shop.event.UserRegisteredEvent;

// relays outbox rows to Kafka on a fixed schedule. Publishing after the DB commit (not during the
// request) is what makes delivery reliable: a committed row is retried until the broker
// acknowledges it. Duplicates on retry are safe — the cart consumer is idempotent.
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

  private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

  private static final int BATCH_SIZE = 100;
  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;
  private final ObjectMapper objectMapper;

  // the FOR UPDATE lock and the published_at update must share one transaction, so this method is
  // @Transactional. The scheduler invokes it through the transactional proxy, so the advice
  // applies.
  @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}")
  @Transactional
  public void publishPendingEvents() {
    List<OutboxEvent> batch = outboxEventRepository.lockUnpublishedBatch(BATCH_SIZE);
    for (OutboxEvent event : batch) {
      try {
        UserRegisteredEvent payload =
            objectMapper.readValue(event.getPayload(), UserRegisteredEvent.class);
        // block for the broker ack: only a confirmed send may stamp published_at
        kafkaTemplate
            .send(event.getTopic(), event.getAggregateId(), payload)
            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        event.markPublished();
      } catch (Exception e) {
        // one unsendable row must not roll back rows already sent in this batch: leave it
        // unpublished and let the next cycle retry it (at-least-once delivery)
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        logger.error("Failed to publish outbox event {}, will retry", event.getId(), e);
      }
    }
  }
}
