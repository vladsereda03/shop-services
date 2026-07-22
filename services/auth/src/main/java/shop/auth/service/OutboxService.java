package shop.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
  private final Tracer tracer;
  private final Propagator propagator;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String topic, String key, Object event) {
    OutboxEvent outboxEvent =
        OutboxEvent.builder()
            .aggregateId(key)
            .eventType(event.getClass().getSimpleName())
            .topic(topic)
            .payload(serialize(event))
            .traceContext(captureTraceContext())
            .createdAt(Instant.now())
            .build();
    outboxEventRepository.save(outboxEvent);
  }

  // snapshot the current trace as propagation headers so the async relay can rejoin it; null when
  // the caller runs outside any trace
  private String captureTraceContext() {
    TraceContext context = tracer.currentTraceContext().context();
    if (context == null) {
      return null;
    }
    Map<String, String> carrier = new HashMap<>();
    propagator.inject(context, carrier, Map::put);
    return serialize(carrier);
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      // an event or trace context that cannot be serialized is a programming error, not runtime
      throw new IllegalStateException("Cannot serialize outbox value " + value.getClass(), e);
    }
  }
}
