package shop.cart.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.cart.model.dto.CartDTO;
import shop.cart.service.CartService;

@RestController
@AllArgsConstructor
@RequestMapping("/carts")
public class CartController {

    private final CartService cartService;

    @GetMapping("/my")
    public CartDTO myCart(@AuthenticationPrincipal Jwt jwt) {
        return CartDTO.from(cartService.getOrCreateCart(currentUserId(jwt)));
    }

    @PostMapping("/my/items")
    public CartDTO addItem(@AuthenticationPrincipal Jwt jwt,
                           @RequestParam("goodId") Long goodId,
                           @RequestParam("quantity") int quantity) {
        return CartDTO.from(cartService.addItem(currentUserId(jwt), goodId, quantity));
    }

    @DeleteMapping("/my")
    public CartDTO clearCart(@AuthenticationPrincipal Jwt jwt) {
        return CartDTO.from(cartService.clearCart(currentUserId(jwt)));
    }

    private Long currentUserId(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        if (uid instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access token has no uid claim");
    }
}
