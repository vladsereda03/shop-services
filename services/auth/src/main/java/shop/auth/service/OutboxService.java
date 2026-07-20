package shop.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shop.auth.model.OutboxEvent;
import shop.auth.repository.OutboxEventRepository;

// records domain events into the outbox table. MANDATORY: the write is only meaningful inside
// the caller's business transaction, so the event and the state change commit atomically (or not
// at all). Calling it without an active transaction is a bug and fails loudly.
@Service
@RequiredArgsConstructor
public class OutboxService {

  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String topic, String key, Object event) {
    OutboxEvent outboxEvent =
        OutboxEvent.builder()
            .aggregateId(key)
            .eventType(event.getClass().getSimpleName())
            .topic(topic)
            .payload(serialize(event))
            .createdAt(Instant.now())
            .build();
    outboxEventRepository.save(outboxEvent);
  }

  private String serialize(Object event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      // a domain event that cannot be serialized is a programming error, not a runtime condition
      throw new IllegalStateException("Cannot serialize outbox event " + event.getClass(), e);
    }
  }
}
