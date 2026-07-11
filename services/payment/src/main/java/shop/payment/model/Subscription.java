package shop.payment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.*;

// recurring payment subscription: LiqPay charges the card on schedule,
// each confirmed charge turns the stored cart snapshot into an order
@Access(AccessType.FIELD)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class Subscription {

  @Id
  @Column(columnDefinition = "bigint")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "subscription_gen")
  @SequenceGenerator(
      name = "subscription_gen",
      sequenceName = "subscription_seq",
      allocationSize = 1)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private String phone;

  @Column(nullable = false)
  private String currencyCode;

  // day | week | month | year — matches both the form options and task.cron.* config keys
  @Column(nullable = false)
  private String periodicity;

  @Column(nullable = false)
  private LocalDateTime startDate;

  // snapshot of the cart at subscribe time: goodId -> (quantity, price)
  @Builder.Default
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "subscription_items", joinColumns = @JoinColumn(name = "subscription_id"))
  @MapKeyColumn(name = "good_id")
  private Map<Long, SubscriptionItem> items = new HashMap<>();

  public long calculatePriceKopeck() {
    return items.values().stream()
        .mapToLong(item -> item.getPriceKopeck() * item.getQuantity())
        .sum();
  }

  public double calculatePrice() {
    return calculatePriceKopeck() / 100.0;
  }

  @Embeddable
  @Getter
  @Setter
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SubscriptionItem {
    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Long priceKopeck;
  }
}
