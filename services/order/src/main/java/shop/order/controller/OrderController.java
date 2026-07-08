package shop.order.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.order.model.dto.OrderDTO;
import shop.order.service.OrderService;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/my")
    public List<OrderDTO> myOrders(@AuthenticationPrincipal Jwt jwt) {
        return orderService.getMyOrders(currentUserId(jwt)).stream()
                .map(OrderDTO::from)
                .toList();
    }

    // checkout: turn the current cart into an order and clear the cart
    @PostMapping("/my")
    public OrderDTO checkout(@AuthenticationPrincipal Jwt jwt) {
        return OrderDTO.from(orderService.checkout(currentUserId(jwt)));
    }

    private Long currentUserId(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        if (uid instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access token has no uid claim");
    }
}
