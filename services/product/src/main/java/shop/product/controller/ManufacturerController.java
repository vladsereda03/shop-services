package shop.product.controller;

import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shop.product.model.dto.ManufacturerDTO;
import shop.product.service.ProductService;

// read-only: manufacturers are seeded directly in the DB (same as the monolith)
@RestController
@AllArgsConstructor
@RequestMapping("/api/manufacturers")
public class ManufacturerController {

  private final ProductService productService;

  @GetMapping()
  public List<ManufacturerDTO> getAll() {
    return productService.getAllManufacturers().stream().map(ManufacturerDTO::new).toList();
  }
}
