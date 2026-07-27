package shop.product.service;

import jakarta.persistence.EntityNotFoundException;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import shop.product.model.Good;
import shop.product.model.Manufacturer;
import shop.product.model.dto.CatalogItemDTO;
import shop.product.model.dto.CreateGoodRequest;
import shop.product.model.dto.ManufacturerDTO;
import shop.product.repository.GoodRepository;
import shop.product.repository.ManufacturerRepository;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final GoodRepository goodRepository;
  private final ManufacturerRepository manufacturerRepository;
  private final ImageStorage imageStorage;

  // Cached catalog list for the hot read path. The projection omits `quantity`, so stock changes
  // (reserve/release) never invalidate it; only catalog composition changes (createGood) evict it.
  // Single logical entry — the list has no arguments — under the fixed key `all`.
  @Cacheable(cacheNames = "catalog", key = "'all'")
  @Transactional(readOnly = true)
  public List<CatalogItemDTO> getCatalog() {
    return goodRepository.findAll().stream().map(this::toCatalogItem).toList();
  }

  private CatalogItemDTO toCatalogItem(Good good) {
    String imageUrl = good.getImageKey() == null ? null : imageStorage.urlFor(good.getImageKey());
    List<ManufacturerDTO> manufacturers =
        good.getManufacturers().stream().map(ManufacturerDTO::new).toList();
    return new CatalogItemDTO(
        good.getId(),
        good.getName(),
        good.getPriceKopeck(),
        good.getDescription(),
        good.getCategory(),
        imageUrl,
        manufacturers);
  }

  @Transactional(readOnly = true)
  public Good getById(long id) {
    return goodRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Good with id " + id + " not found"));
  }

  @Transactional(readOnly = true)
  public List<Manufacturer> getAllManufacturers() {
    return manufacturerRepository.findAll();
  }

  @Transactional
  public void reserve(long goodId, int quantity) {
    Good good = findForUpdate(goodId, quantity);
    if (good.getQuantity() < quantity) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Not enough stock for good "
              + goodId
              + ": requested "
              + quantity
              + ", available "
              + good.getQuantity());
    }
    good.setQuantity(good.getQuantity() - quantity);
  }

  @Transactional
  public void release(long goodId, int quantity) {
    Good good = findForUpdate(goodId, quantity);
    good.setQuantity(good.getQuantity() + quantity);
  }

  // a new catalog entry changes the list, so drop the cached projection
  @CacheEvict(cacheNames = "catalog", allEntries = true)
  @Transactional
  public Good createGood(CreateGoodRequest request) {
    if (request.getName() == null || request.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
    }
    if (request.getPriceKopeck() < 0 || request.getQuantity() < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Price and quantity must not be negative");
    }

    byte[] image = null;
    if (request.getImageBase64() != null && !request.getImageBase64().isBlank()) {
      try {
        image = Base64.getDecoder().decode(request.getImageBase64());
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "imageBase64 is not valid base64");
      }
    }

    Good good =
        new Good(
            request.getName(),
            request.getPriceKopeck(),
            request.getDescription(),
            request.getCategory(),
            manufacturerRepository.findAllById(request.getManufacturerIds()));
    good.setQuantity(request.getQuantity());

    if (image != null) {
      // bytes go to object storage; the catalog row keeps only the key
      good.setImageKey(imageStorage.put(image));
    }

    return goodRepository.saveAndFlush(good);
  }

  private Good findForUpdate(long goodId, int quantity) {
    if (quantity <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
    }
    return goodRepository
        .findWithLockById(goodId)
        .orElseThrow(() -> new EntityNotFoundException("Good with id " + goodId + " not found"));
  }
}
