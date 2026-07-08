package shop.payment.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import shop.payment.model.Subscription;

import java.time.LocalDateTime;

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

    public static SubscriptionDTO from(Subscription subscription) {
        return new SubscriptionDTO(
                subscription.getId(),
                subscription.getPhone(),
                subscription.getCurrencyCode(),
                subscription.getPeriodicity(),
                subscription.getStartDate(),
                subscription.calculatePrice());
    }
}
