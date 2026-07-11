package shop.product.model.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// admin request to add a good to the catalog (image travels as base64 JSON field)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateGoodRequest {

  private String name;
  private long priceKopeck;
  private String description;
  private String category;
  private int quantity;
  private String imageBase64;
  private List<Long> manufacturerIds = new ArrayList<>();
}
