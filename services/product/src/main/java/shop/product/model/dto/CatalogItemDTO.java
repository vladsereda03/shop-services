package shop.product.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cached catalog-list projection (Р5): the same fields as GoodDTO minus the volatile `quantity`, so
// the list can be cached in Redis without a stock change (reserve/release) ever invalidating it.
// Availability is served fresh by the per-item endpoint (GET /api/products/{id}), which is not
// cached.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CatalogItemDTO {
  private long id;
  private String name;
  private long priceKopeck;
  private String description;
  private String category;
  private String imageUrl;
  private List<ManufacturerDTO> manufacturers;
}
