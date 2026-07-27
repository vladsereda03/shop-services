package shop.order.service;

import shop.order.model.dto.OrderDTO;

// Published after an order is persisted; consumed by OrderStreamService (after commit) to feed the
// SSE "live orders" stream. Carries the detached DTO, not the entity, so the post-commit emit onto
// the reactive sink needs no open session and no re-read.
public record OrderCreatedEvent(OrderDTO order) {}
