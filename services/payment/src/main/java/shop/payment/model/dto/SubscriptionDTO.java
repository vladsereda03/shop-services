package shop.payment.model.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import shop.payment.model.Subscription;

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
  // null while active; set when cancelled — the client shows the status from it
  private Instant cancelledAt;
  private double totalPrice;

  public static SubscriptionDTO from(Subscription subscription) {
    return new SubscriptionDTO(
        subscription.getId(),
        subscription.getPhone(),
        subscription.getCurrencyCode(),
        subscription.getPeriodicity(),
        subscription.getStartDate(),
        subscription.getCancelledAt(),
        subscription.calculatePrice());
  }
}
