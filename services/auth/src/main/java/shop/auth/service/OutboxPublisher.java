package shop.auth.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// drives the outbox relay on a fixed schedule. Publishing after the DB commit (not during the
// request) is what makes delivery reliable: a committed row is retried until the broker
// acknowledges it. Duplicates on retry are safe — the cart consumer is idempotent.
//
// SmartLifecycle so polling can be stopped cleanly: Spring calls stop() while the context shuts
// down (before the datasource closes), and the integration test calls it before tearing down its
// database container — either way a poll can no longer fire against a dead connection and hang.
@Service
@RequiredArgsConstructor
public class OutboxPublisher implements SmartLifecycle {

  private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxRelay outboxRelay;

  private volatile boolean running = false;

  @Override
  public void start() {
    running = true;
  }

  @Override
  public void stop() {
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}")
  public void publishPendingEvents() {
    if (!running) {
      return;
    }
    try {
      outboxRelay.relayPendingEvents();
    } catch (Exception e) {
      // a failed poll (e.g. the DB briefly unreachable) must not reach the scheduler's error
      // handler as an ERROR with a stack trace; the next cycle retries
      logger.warn("Outbox relay cycle failed, will retry next cycle: {}", e.getMessage());
    }
  }
}
