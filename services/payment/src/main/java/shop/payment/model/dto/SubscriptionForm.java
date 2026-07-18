package shop.payment.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// subscription request filled by the user on the client's subscription page;
// validated at the MVC edge (@Valid in SubscriptionController)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionForm {

  @NotBlank private String phone;

  @NotNull private LocalTime startTime;

  @NotNull private LocalDate startDate;

  // must match the task.cron.* config keys of the charge scheduler
  @NotBlank
  @Pattern(regexp = "day|week|month|year")
  private String periodicity;

  @NotBlank
  @Pattern(regexp = "\\d{13,19}")
  private String cardNumber;

  @Min(1)
  @Max(12)
  private int expMonth;

  // expiry plausibility is the payment provider's concern, not ours
  @Positive private int expYear;

  @NotBlank
  @Pattern(regexp = "\\d{3,4}")
  private String cvv;
}
