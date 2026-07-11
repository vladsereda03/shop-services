package shop.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shop.product.model.Manufacturer;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {}
