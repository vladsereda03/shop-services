package shop.cart.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shop.cart.model.dto.CartDTO;
import shop.cart.service.CartService;

// service-to-service API, protected by carts.read / carts.write scopes
@RestController
@AllArgsConstructor
@RequestMapping("/internal/carts")
public class InternalCartController {

    private final CartService cartService;

    @GetMapping("/{userId}")
    public CartDTO cartOfUser(@PathVariable("userId") Long userId) {
        return CartDTO.from(cartService.getOrCreateCart(userId));
    }

    @PostMapping("/{userId}/checkout-clear")
    public CartDTO checkoutClear(@PathVariable("userId") Long userId) {
        return CartDTO.from(cartService.clearAfterCheckout(userId));
    }
}
