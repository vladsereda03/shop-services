package shop.client.dto;

import java.util.List;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GoodDTO {
  private long id;
  private String name;
  private long priceKopeck;
  private String description;
  private String category;
  // public object-storage URL of the image; the browser fetches the bytes from object storage
  private String imageUrl;

  private List<ManufacturerDTO> manufacturers;

  private int quantity;

  public double getPriceGrn() {
    return (double) (priceKopeck) / 100;
  }
}
