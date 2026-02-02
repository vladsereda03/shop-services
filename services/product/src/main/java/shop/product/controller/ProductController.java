package shop.product.controller;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shop.product.model.dto.GoodDTO;
import shop.product.repository.GoodRepository;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("/goods")
public class ProductController {

    private final GoodRepository goodRepository;

    @GetMapping()
    public List<GoodDTO> getAll() {

        return goodRepository.findAll().stream().map(GoodDTO::new).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public GoodDTO getById(@PathVariable("id") long id) {

        GoodDTO goodDTO = goodRepository.findById(id).map(GoodDTO::new)
        .orElseThrow(() -> new EntityNotFoundException("Good with id " + id + " not found"));

        return goodDTO;
    }
}
