package shop.client.dto;

import java.time.Instant;
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
public class OrderDTO {

  private Long id;
  private Long userId;
  private Instant createdAt;
  private List<OrderItemDTO> items = new ArrayList<>();
  private double totalPrice;
}
