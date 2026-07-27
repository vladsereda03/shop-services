package shop.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import shop.order.model.dto.OrderDTO;

class OrderStreamServiceTest {

  @Test
  void streamDeliversOnlyTheSubscribersOwnOrders() {
    OrderStreamService service = new OrderStreamService();
    Flux<OrderDTO> userOne = service.streamFor(1L);

    StepVerifier.create(userOne)
        // own order → delivered
        .then(() -> service.onOrderCreated(new OrderCreatedEvent(order(10L, 1L))))
        .assertNext(order -> assertThat(order.getId()).isEqualTo(10L))
        // another user's order → filtered out (no signal on this stream)
        .then(() -> service.onOrderCreated(new OrderCreatedEvent(order(11L, 2L))))
        // own order again → delivered, proving the foreign one was skipped, not just delayed
        .then(() -> service.onOrderCreated(new OrderCreatedEvent(order(12L, 1L))))
        .assertNext(order -> assertThat(order.getId()).isEqualTo(12L))
        .thenCancel()
        .verify(Duration.ofSeconds(5));
  }

  private static OrderDTO order(long id, long userId) {
    return new OrderDTO(id, userId, Instant.now(), List.of(), 0.0);
  }
}
