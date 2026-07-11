package shop.order.model.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
