package shop.order.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Access(AccessType.FIELD)
@Entity
@Table(name = "orders")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class Order {

    @Id
    @Column(columnDefinition = "bigint")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_gen")
    @SequenceGenerator(name = "order_gen", sequenceName = "order_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant createdAt;

    // snapshot of the cart at checkout time: goodId -> (quantity, price)
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "good_id")
    private Map<Long, OrderItem> items = new HashMap<>();

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
    public static class OrderItem {
        @Column(nullable = false)
        private Integer quantity;

        @Column(nullable = false)
        private Long priceKopeck;
    }

}
