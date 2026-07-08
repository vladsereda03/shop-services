package shop.cart.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductDTO {

    private long id;
    private String name;
    private long priceKopeck;
    private int quantity;
}
