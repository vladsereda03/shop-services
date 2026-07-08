package shop.product.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import shop.product.model.Good;
import shop.product.repository.GoodRepository;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final GoodRepository goodRepository;

    @Transactional
    public void reserve(long goodId, int quantity) {
        Good good = findForUpdate(goodId, quantity);
        if (good.getQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough stock for good " + goodId + ": requested " + quantity
                            + ", available " + good.getQuantity());
        }
        good.setQuantity(good.getQuantity() - quantity);
    }

    @Transactional
    public void release(long goodId, int quantity) {
        Good good = findForUpdate(goodId, quantity);
        good.setQuantity(good.getQuantity() + quantity);
    }

    private Good findForUpdate(long goodId, int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
        return goodRepository.findWithLockById(goodId)
                .orElseThrow(() -> new EntityNotFoundException("Good with id " + goodId + " not found"));
    }
}
