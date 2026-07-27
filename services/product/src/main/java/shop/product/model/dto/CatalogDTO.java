package shop.product.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cache wrapper for the catalog projection (Р5). GenericJackson2JsonRedisSerializer cannot
// round-trip a top-level List: it writes a plain array, but reading back into Object with default
// typing expects a ["java.util.ArrayList", ...] type wrapper, which throws on the cache hit. The
// cached value is this concrete type instead — its @class tags the root and the list rides as a
// typed field, which round-trips cleanly. The controller unwraps it, so the API still returns a
// plain JSON array.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CatalogDTO {
  private List<CatalogItemDTO> items;
}
