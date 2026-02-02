package shop.client.controller;

import lombok.AllArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClient;
import shop.client.dto.GoodDTO;
import shop.client.dto.UserDTO;

import java.security.Principal;
import java.util.List;

@Controller
@AllArgsConstructor
public class ProductController {

    private final RestClient restClient;

    @GetMapping("/")
    public String showAll(Principal principal, Model model) {
        String username = principal.getName();

        UserDTO userDto = restClient.get()
                .uri("http://auth.local:9000/connect/userinfo", username)
                .retrieve()
                .body(UserDTO.class);

        model.addAttribute("user", userDto);

        List<GoodDTO> goodDTOS = restClient.get().uri("http://localhost:8082/goods").retrieve()
                .body(new ParameterizedTypeReference<List<GoodDTO>>() {});
        model.addAttribute("goods", goodDTOS);

        return "assortment/goods";
    }

    @GetMapping("/goods/{id}")
    public String showByID(Principal principal, @PathVariable("id") long id, Model model) {

        String username = principal.getName();

        UserDTO userDto = restClient.get()
                .uri("http://auth.local:9000/connect/userinfo", username)
                .retrieve()
                .body(UserDTO.class);

        model.addAttribute("user", userDto);



        GoodDTO goodDTO = restClient.get().uri("http://localhost:8082/goods/{id}", id).retrieve()
                .body(GoodDTO.class);


        model.addAttribute("good", goodDTO);
        model.addAttribute("isAvailable", goodDTO.getQuantity() > 0 ? "наявний" : "відсутній");
        model.addAttribute("category", goodDTO.getCategory());

        return "assortment/good";
    }
}


