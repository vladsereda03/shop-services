package shop.client.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// response of payment-service /subscriptions/my endpoints
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
  // null while active; set once cancelled — drives the status shown on the subscriptions page
  private Instant cancelledAt;
  private double totalPrice;
}
