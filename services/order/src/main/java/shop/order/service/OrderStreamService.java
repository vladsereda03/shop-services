package shop.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import shop.order.model.dto.OrderDTO;

// Reactive fan-out for the "live orders" feed (Р7). An order committed anywhere in the service — a
// LiqPay callback, a scheduled recurring charge, a manual checkout — is pushed to any browser
// currently streaming its own orders, without polling.
//
// The sink is multicast + directBestEffort: a pure live feed with no replay and no unbounded
// buffering. Events emitted while a given user has no open stream are simply dropped for them,
// which is exactly right — a page that is not open does not need them, and the order is still
// persisted regardless.
@Service
public class OrderStreamService {

  private final Sinks.Many<OrderDTO> sink = Sinks.many().multicast().directBestEffort();

  // AFTER_COMMIT, so a rolled-back checkout (e.g. the post-order cart clear failed) never emits a
  // phantom order onto the feed.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderCreated(OrderCreatedEvent event) {
    sink.tryEmitNext(event.order());
  }

  // the caller's own committed orders, as they happen
  public Flux<OrderDTO> streamFor(long userId) {
    return sink.asFlux().filter(order -> order.getUserId() == userId);
  }
}
