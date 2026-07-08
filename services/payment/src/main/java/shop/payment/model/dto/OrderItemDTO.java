package shop.payment.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// request body of order-service POST /internal/orders/{userId}
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {

    private Long goodId;
    private Integer quantity;
    private Long priceKopeck;
}
