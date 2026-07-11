package shop.client.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
