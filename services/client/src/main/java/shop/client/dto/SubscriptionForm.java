package shop.client.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// bound to the subscription page form, forwarded as-is to payment POST /subscriptions/my
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionForm {

  private String phone;
  private LocalTime startTime;
  private LocalDate startDate;
  private String periodicity;
  private String cardNumber;
  private int expMonth;
  private int expYear;
  private String cvv;
}
