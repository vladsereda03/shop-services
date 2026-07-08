package shop.product.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shop.product.model.dto.ManufacturerDTO;
import shop.product.repository.ManufacturerRepository;

import java.util.List;

// read-only: manufacturers are seeded directly in the DB (same as the monolith)
@RestController
@AllArgsConstructor
@RequestMapping("/api/manufacturers")
public class ManufacturerController {

    private final ManufacturerRepository manufacturerRepository;

    @GetMapping()
    public List<ManufacturerDTO> getAll() {
        return manufacturerRepository.findAll().stream()
                .map(ManufacturerDTO::new)
                .toList();
    }
}
