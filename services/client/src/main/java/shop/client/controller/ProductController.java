package shop.client.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClient;
import shop.client.dto.GoodDTO;
import shop.client.dto.UserDTO;

import java.util.List;

@Controller
public class ProductController {

    private final RestClient restClient;
    private final String authBaseUrl;
    private final String productBaseUrl;

    public ProductController(RestClient restClient,
                             @Value("${services.auth.base-url}") String authBaseUrl,
                             @Value("${services.product.base-url}") String productBaseUrl) {
        this.restClient = restClient;
        this.authBaseUrl = authBaseUrl;
        this.productBaseUrl = productBaseUrl;
    }

    @GetMapping("/")
    public String showAll(Model model) {
        UserDTO userDto = restClient.get()
                .uri(authBaseUrl + "/connect/userinfo")
                .retrieve()
                .body(UserDTO.class);

        model.addAttribute("user", userDto);

        List<GoodDTO> goodDTOS = restClient.get()
                .uri(productBaseUrl + "/api/products")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GoodDTO>>() {});
        model.addAttribute("goods", goodDTOS);

        return "assortment/goods";
    }

    @GetMapping("/goods/{id}")
    public String showByID(@PathVariable("id") long id, Model model) {
        UserDTO userDto = restClient.get()
                .uri(authBaseUrl + "/connect/userinfo")
                .retrieve()
                .body(UserDTO.class);

        model.addAttribute("user", userDto);

        GoodDTO goodDTO = restClient.get()
                .uri(productBaseUrl + "/api/products/{id}", id)
                .retrieve()
                .body(GoodDTO.class);

        model.addAttribute("good", goodDTO);
        model.addAttribute("isAvailable", goodDTO.getQuantity() > 0 ? "наявний" : "відсутній");
        model.addAttribute("category", goodDTO.getCategory());

        return "assortment/good";
    }
}
