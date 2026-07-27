package shop.product.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shop.product.model.Good;
import shop.product.model.dto.CatalogItemDTO;
import shop.product.model.dto.CreateGoodRequest;
import shop.product.model.dto.GoodDTO;
import shop.product.service.ImageStorage;
import shop.product.service.ProductService;

@RestController
@AllArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService productService;
  private final ImageStorage imageStorage;

  // cached catalog-list projection (no per-item stock); availability comes from getById
  @GetMapping()
  public List<CatalogItemDTO> getAll() {
    return productService.getCatalog();
  }

  @GetMapping("/{id}")
  public GoodDTO getById(@PathVariable("id") long id) {
    return toDto(productService.getById(id));
  }

  // catalog management: requires the ADMIN role (see SecurityConfig)
  @PostMapping()
  public GoodDTO create(@Valid @RequestBody CreateGoodRequest request) {
    return toDto(productService.createGood(request));
  }

  // map entity -> DTO, deriving the public image URL from the stored key. Legacy rows without a
  // key keep their inline bytes so the client can still render them until they are migrated.
  private GoodDTO toDto(Good good) {
    GoodDTO dto = new GoodDTO(good);
    if (good.getImageKey() != null) {
      dto.setImageUrl(imageStorage.urlFor(good.getImageKey()));
    }
    return dto;
  }

  @PostMapping("/{id}/reserve")
  public void reserve(@PathVariable("id") long id, @RequestParam("quantity") int quantity) {
    productService.reserve(id, quantity);
  }

  @PostMapping("/{id}/release")
  public void release(@PathVariable("id") long id, @RequestParam("quantity") int quantity) {
    productService.release(id, quantity);
  }
}
