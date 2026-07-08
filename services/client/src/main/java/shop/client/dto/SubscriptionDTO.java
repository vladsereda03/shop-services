package shop.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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
