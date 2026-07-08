package shop.cart.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import shop.cart.model.Cart;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartDTO {

    private Long id;
    private Long userId;
    private List<CartItemDTO> items;
    private double totalPrice;

    public static CartDTO from(Cart cart) {
        List<CartItemDTO> items = cart.getItems().entrySet().stream()
                .map(entry -> new CartItemDTO(entry.getKey(),
                        entry.getValue().getQuantity(),
                        entry.getValue().getPriceKopeck()))
                .toList();
        return new CartDTO(cart.getId(), cart.getUserId(), items, cart.calculatePrice());
    }
}
