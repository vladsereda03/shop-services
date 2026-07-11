package shop.cart.model;

import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;
import lombok.*;

@Access(AccessType.FIELD)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class Cart {

  @Id
  @Column(columnDefinition = "bigint")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_gen")
  @SequenceGenerator(name = "cart_gen", sequenceName = "cart_seq", allocationSize = 1)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Builder.Default
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "cart_items", joinColumns = @JoinColumn(name = "cart_id"))
  @MapKeyColumn(name = "good_id")
  private Map<Long, CartItem> items = new HashMap<>();

  public void addItem(Long goodId, int quantity, long priceKopeck) {
    items.merge(
        goodId,
        new CartItem(quantity, priceKopeck),
        (existing, added) -> new CartItem(existing.getQuantity() + quantity, priceKopeck));
  }

  public void clearItems() {
    items.clear();
  }

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
  public static class CartItem {
    @Column(nullable = false)
    private Integer quantity;

    // price snapshot at the moment the item was put into the cart
    @Column(nullable = false)
    private Long priceKopeck;
  }
}
