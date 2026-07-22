package shop.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shop.auth.model.OutboxEvent;
import shop.auth.repository.OutboxEventRepository;
import shop.event.UserRegisteredEvent;

// the transactional half of the outbox relay: claims a batch and publishes it. Kept in a separate
// bean from the @Scheduled trigger so the shutdown gate there runs BEFORE this transaction opens
// (and so the @Transactional proxy is honoured — a self-invocation from the scheduler would bypass
// it, the same AOP pitfall as the resilience clients).
@Service
@RequiredArgsConstructor
public class OutboxRelay {

  private static final Logger logger = LoggerFactory.getLogger(OutboxRelay.class);

  private static final int BATCH_SIZE = 100;
  private static final long SEND_TIMEOUT_SECONDS = 10;
  private static final TypeReference<Map<String, String>> TRACE_CARRIER_TYPE =
      new TypeReference<>() {};

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final Tracer tracer;
  private final Propagator propagator;

  // the FOR UPDATE lock and the published_at update must share one transaction, hence
  // @Transactional
  @Transactional
  public void relayPendingEvents() {
    List<OutboxEvent> batch = outboxEventRepository.lockUnpublishedBatch(BATCH_SIZE);
    for (OutboxEvent event : batch) {
      // resume the recording request's trace so the send (and the consumer that receives it)
      // appears under the same signup trace instead of a disconnected one on this poller thread
      Span span = publishSpan(event);
      try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
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
        span.error(e);
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        logger.error("Failed to publish outbox event {}, will retry", event.getId(), e);
      } finally {
        span.end();
      }
    }
  }

  // continue the trace stored at record time, or start a fresh span when the event carries none
  private Span publishSpan(OutboxEvent event) {
    Span.Builder builder =
        event.getTraceContext() == null
            ? tracer.spanBuilder()
            : propagator.extract(deserializeCarrier(event.getTraceContext()), Map::get);
    return builder.name("outbox.publish").start();
  }

  private Map<String, String> deserializeCarrier(String traceContext) {
    try {
      return objectMapper.readValue(traceContext, TRACE_CARRIER_TYPE);
    } catch (JsonProcessingException e) {
      // a corrupt trace header must never stop delivery — fall back to a fresh span
      logger.warn("Cannot parse stored trace context, publishing without it: {}", e.getMessage());
      return Map.of();
    }
  }
}
