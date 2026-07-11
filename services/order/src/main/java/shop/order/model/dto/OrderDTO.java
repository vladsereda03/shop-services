package shop.order.model.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import shop.order.model.Order;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

  private Long id;
  private Long userId;
  private Instant createdAt;
  private List<OrderItemDTO> items;
  private double totalPrice;

  public static OrderDTO from(Order order) {
    List<OrderItemDTO> items =
        order.getItems().entrySet().stream()
            .map(
                entry ->
                    new OrderItemDTO(
                        entry.getKey(),
                        entry.getValue().getQuantity(),
                        entry.getValue().getPriceKopeck()))
            .toList();
    return new OrderDTO(
        order.getId(), order.getUserId(), order.getCreatedAt(), items, order.calculatePrice());
  }
}
