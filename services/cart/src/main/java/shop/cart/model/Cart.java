package shop.cart.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Access(AccessType.FIELD)
@Entity
@SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)
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
    @ElementCollection
    @CollectionTable(name = "cart_items", joinColumns = @JoinColumn(name = "cart_id"))
    @MapKeyColumn(name = "good_id")
    private Map<Long, CartItem> items = new HashMap<>();

    public Cart(Map<Long, CartItem> items) {
        this.items = items;
    }

    public Cart(Cart cart) {
        this.items = new HashMap<>(cart.getItems());
    }

    public double calculatePrice() {
        ////////////
        return items.values().stream().mapToDouble(cartItem -> cartItem.priceKopeck * cartItem.getQuantity()).sum() / 100.0;
    }

    @Embeddable
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public class CartItem {
        private Integer quantity;
        private Double priceKopeck;
    }

}
