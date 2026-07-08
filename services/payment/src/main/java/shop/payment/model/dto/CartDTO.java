package shop.payment.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// response of cart-service GET /internal/carts/{userId}
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartDTO {

    private Long id;
    private Long userId;
    private List<CartItemDTO> items = new ArrayList<>();
    private double totalPrice;
}
