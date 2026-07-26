package shop.product.model.dto;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import shop.product.model.Good;

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
  // public object-storage URL of the image; null only for legacy rows not yet backfilled. Image
  // bytes are no longer carried in the payload — the browser fetches them from object storage.
  private String imageUrl;

  private List<ManufacturerDTO> manufacturers;

  private int quantity;

  public GoodDTO(Good good) {
    this.id = good.getId();
    this.name = good.getName();
    this.priceKopeck = good.getPriceKopeck();
    this.description = good.getDescription();
    this.category = good.getCategory();
    this.quantity = good.getQuantity();
    this.manufacturers =
        good.getManufacturers().stream().map(ManufacturerDTO::new).collect(Collectors.toList());
  }
}
