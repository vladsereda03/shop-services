package shop.client.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// form-backing object of /goods/add and, at the same time,
// the JSON body of POST /api/products (mirror of product's CreateGoodRequest)
@Getter
@Setter
@NoArgsConstructor
public class CreateGoodRequest {
  private String name;
  private long priceKopeck;
  private String description;
  private String category;
  private int quantity;
  private String imageBase64;
  private List<Long> manufacturerIds;
}
