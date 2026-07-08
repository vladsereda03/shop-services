package shop.payment.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

// subscription request filled by the user on the client's subscription page
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
