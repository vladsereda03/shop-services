package shop.product.service;

import jakarta.persistence.EntityNotFoundException;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import shop.product.model.Good;
import shop.product.model.Manufacturer;
import shop.product.model.dto.CreateGoodRequest;
import shop.product.repository.GoodRepository;
import shop.product.repository.ManufacturerRepository;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final GoodRepository goodRepository;
  private final ManufacturerRepository manufacturerRepository;

  @Transactional(readOnly = true)
  public List<Good> getAll() {
    return goodRepository.findAll();
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
            image,
            manufacturerRepository.findAllById(request.getManufacturerIds()));
    good.setQuantity(request.getQuantity());

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
