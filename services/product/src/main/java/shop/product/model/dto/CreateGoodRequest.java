package shop.product.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// admin request to add a good to the catalog (image travels as base64 JSON field);
// validated at the MVC edge (@Valid in ProductController), the service keeps its
// own checks as defence in depth
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateGoodRequest {

  @NotBlank private String name;

  @Positive private long priceKopeck;

  private String description;
  private String category;

  @PositiveOrZero private int quantity;

  private String imageBase64;
  private List<Long> manufacturerIds = new ArrayList<>();
}
