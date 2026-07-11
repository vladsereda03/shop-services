package shop.client.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// response of payment-service POST /subscriptions/my
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDTO {

  private Long id;
  private String phone;
  private String currencyCode;
  private String periodicity;
  private LocalDateTime startDate;
  private double totalPrice;
}
