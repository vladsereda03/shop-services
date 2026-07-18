package shop.product.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shop.product.model.dto.CreateGoodRequest;
import shop.product.model.dto.GoodDTO;
import shop.product.repository.GoodRepository;
import shop.product.service.ProductService;

@RestController
@AllArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

  private final GoodRepository goodRepository;
  private final ProductService productService;

  @GetMapping()
  public List<GoodDTO> getAll() {

    return goodRepository.findAll().stream().map(GoodDTO::new).collect(Collectors.toList());
  }

  @GetMapping("/{id}")
  public GoodDTO getById(@PathVariable("id") long id) {

    GoodDTO goodDTO =
        goodRepository
            .findById(id)
            .map(GoodDTO::new)
            .orElseThrow(() -> new EntityNotFoundException("Good with id " + id + " not found"));

    return goodDTO;
  }

  // catalog management: requires the ADMIN role (see SecurityConfig)
  @PostMapping()
  public GoodDTO create(@Valid @RequestBody CreateGoodRequest request) {
    return new GoodDTO(productService.createGood(request));
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
