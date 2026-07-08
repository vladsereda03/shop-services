package shop.order.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shop.order.model.dto.OrderDTO;
import shop.order.service.OrderService;

// service-to-service API: called by payment after a confirmed LiqPay payment
@RestController
@AllArgsConstructor
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;

    @PostMapping("/{userId}/checkout")
    public OrderDTO checkout(@PathVariable Long userId) {
        return OrderDTO.from(orderService.checkout(userId));
    }
}
