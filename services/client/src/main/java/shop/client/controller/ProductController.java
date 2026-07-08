package shop.client.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shop.client.dto.CreateGoodRequest;
import shop.client.dto.GoodDTO;
import shop.client.dto.ManufacturerDTO;
import shop.client.dto.UserDTO;

import java.io.IOException;
import java.util.Base64;
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

    // admin only — enforced both here (SecurityConfig) and in product (hasRole ADMIN)
    @GetMapping("/goods/add")
    public String showAddForm(Model model) {
        List<ManufacturerDTO> manufacturers = restClient.get()
                .uri(productBaseUrl + "/api/manufacturers")
                .retrieve()
                .body(new ParameterizedTypeReference<List<ManufacturerDTO>>() {});

        model.addAttribute("good", new CreateGoodRequest());
        model.addAttribute("manufacturers", manufacturers);
        return "assortment/add_good";
    }

    @PostMapping("/goods/add")
    public String addGood(@ModelAttribute("good") CreateGoodRequest good,
                          @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                          RedirectAttributes redirectAttributes) throws IOException {

        if (imageFile != null && !imageFile.isEmpty()) {
            good.setImageBase64(Base64.getEncoder().encodeToString(imageFile.getBytes()));
        }

        try {
            restClient.post()
                    .uri(productBaseUrl + "/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(good)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            redirectAttributes.addFlashAttribute("addGoodError",
                    "Не вдалося додати товар (" + e.getStatusCode().value() + ")");
            return "redirect:/goods/add";
        }

        return "redirect:/";
    }
}
