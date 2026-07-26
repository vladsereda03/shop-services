package shop.auth.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import shop.auth.repository.OutboxEventRepository;

// Exposes how far the outbox has fallen behind, so an alert can catch a stalled relay before
// users notice missing carts. The gauge value is the age in seconds of the oldest unpublished
// event, or 0 when the outbox is drained. Boot binds every MeterBinder bean to the registry
// automatically; the value is read lazily on each scrape, over the same partial index the poller
// uses, so it stays cheap while the outbox is healthy.
@Component
@RequiredArgsConstructor
public class OutboxMetrics implements MeterBinder {

  private final OutboxEventRepository outboxEventRepository;

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("outbox.unpublished.age", this, OutboxMetrics::oldestUnpublishedAgeSeconds)
        .baseUnit("seconds")
        .description("Age of the oldest unpublished outbox event; 0 when the outbox is drained")
        .register(registry);
  }

  private double oldestUnpublishedAgeSeconds() {
    Instant oldest = outboxEventRepository.findOldestUnpublishedCreatedAt();
    if (oldest == null) {
      return 0.0;
    }
    return Math.max(0L, Duration.between(oldest, Instant.now()).toMillis()) / 1000.0;
  }
}
